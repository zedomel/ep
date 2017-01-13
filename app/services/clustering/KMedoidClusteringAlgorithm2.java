package services.clustering;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.carrot2.core.Cluster;
import org.carrot2.core.Document;
import org.carrot2.core.LanguageCode;
import org.carrot2.core.ProcessingComponentBase;
import org.carrot2.core.ProcessingException;
import org.carrot2.core.attribute.AttributeNames;
import org.carrot2.core.attribute.CommonAttributes;
import org.carrot2.core.attribute.Init;
import org.carrot2.core.attribute.Internal;
import org.carrot2.core.attribute.Processing;
import org.carrot2.mahout.math.function.Functions;
import org.carrot2.mahout.math.matrix.DoubleMatrix1D;
import org.carrot2.mahout.math.matrix.DoubleMatrix2D;
import org.carrot2.mahout.math.matrix.impl.DenseDoubleMatrix1D;
import org.carrot2.mahout.math.matrix.impl.SparseDoubleMatrix1D;
import org.carrot2.shaded.guava.common.collect.Lists;
import org.carrot2.text.analysis.ITokenizer;
import org.carrot2.text.clustering.IMonolingualClusteringAlgorithm;
import org.carrot2.text.clustering.MultilingualClustering;
import org.carrot2.text.preprocessing.LabelFormatter;
import org.carrot2.text.preprocessing.PreprocessingContext;
import org.carrot2.text.preprocessing.pipeline.BasicPreprocessingPipeline;
import org.carrot2.text.preprocessing.pipeline.IPreprocessingPipeline;
import org.carrot2.text.vsm.TermDocumentMatrixBuilder;
import org.carrot2.text.vsm.TermDocumentMatrixReducer;
import org.carrot2.text.vsm.VectorSpaceModelContext;
import org.carrot2.util.attribute.Attribute;
import org.carrot2.util.attribute.AttributeLevel;
import org.carrot2.util.attribute.Bindable;
import org.carrot2.util.attribute.DefaultGroups;
import org.carrot2.util.attribute.Group;
import org.carrot2.util.attribute.Input;
import org.carrot2.util.attribute.Label;
import org.carrot2.util.attribute.Level;
import org.carrot2.util.attribute.Output;
import org.carrot2.util.attribute.Required;
import org.carrot2.util.attribute.constraint.ImplementingClasses;
import org.carrot2.util.attribute.constraint.IntRange;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIntHashMap;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.IntIntCursor;
import com.carrotsearch.hppc.sorting.IndirectComparator;
import com.carrotsearch.hppc.sorting.IndirectSort;

@Bindable(prefix = "KMedoidClusteringAlgorithm", inherit = CommonAttributes.class)
public class KMedoidClusteringAlgorithm2 extends ProcessingComponentBase {

	public static final String NUM_CLUSTERS = "num_clusters";

	public static final String DISTANCE_MEASURE = "distance_measure";

	public static final String MAX_ITERATIONS = "max_iterations";

	public static final String NUM_NEIGHBORS = "num_neighbors";

	@Processing
	@Input
	@Required
	@Internal
	@Attribute(key = AttributeNames.DOCUMENTS, inherit = true)
	public List<Document> documents;

	@Processing
	@Output
	@Internal
	@Attribute(key = AttributeNames.CLUSTERS, inherit = true)
	public List<Cluster> clusters = null;

	/**
	 * The number of clusters to create. The algorithm will create at most the specified
	 * number of clusters.
	 */
	@Processing
	@Input
	@Attribute(key = NUM_CLUSTERS)
	@IntRange(min = 1)
	@Group(DefaultGroups.CLUSTERS)
	@Level(AttributeLevel.BASIC)
	@Label("Cluster count")
	public int numClusters = 2;

	/**
	 * The maximum number of k-means iterations to perform.
	 */
	@Processing
	@Input
	@Attribute(key = MAX_ITERATIONS)
	@IntRange(min = 1)
	@Level(AttributeLevel.BASIC)
	@Label("Maximum iterations")
	public int maxIterations = 15;

	/**
	 * Label count. The minimum number of labels to return for each cluster.
	 */
	@Processing
	@Input
	@Attribute
	@IntRange(min = 1, max = 10)
	@Group(DefaultGroups.CLUSTERS)
	@Level(AttributeLevel.BASIC)
	@Label("Label count")
	public int labelCount = 3;

	/**
	 * Common preprocessing tasks handler.
	 */
	@Init
	@Input
	@Attribute
	@Internal
	@ImplementingClasses(classes = {}, strict = false)
	@Level(AttributeLevel.ADVANCED)
	public IPreprocessingPipeline preprocessingPipeline = new BasicPreprocessingPipeline();
	
	@Processing
	@Input
	@Attribute(key = DISTANCE_MEASURE)
	@ImplementingClasses(classes = {EuclideanDistance.class}, strict = false)
	public DistanceMeasure dm = new EuclideanDistance();

	/**
	 * Term-document matrix builder for the algorithm, contains bindable attributes.
	 */
	public final TermDocumentMatrixBuilder matrixBuilder = new TermDocumentMatrixBuilder();

	/**
	 * Term-document matrix reducer for the algorithm, contains bindable attributes.
	 */
	public final TermDocumentMatrixReducer matrixReducer = new TermDocumentMatrixReducer();

	/**
	 * Cluster label formatter, contains bindable attributes.
	 */
	public final LabelFormatter labelFormatter = new LabelFormatter();

	/**
	 * A helper for performing multilingual clustering.
	 */
	public final MultilingualClustering multilingualClustering = new MultilingualClustering();

	private Random rg = new Random(System.currentTimeMillis());

	@Override
	public void process() throws ProcessingException
	{
		// There is a tiny trick here to support multilingual clustering without
		// refactoring the whole component: we remember the original list of documents
		// and invoke clustering for each language separately within the 
		// IMonolingualClusteringAlgorithm implementation below. This is safe because
		// processing components are not thread-safe by definition and 
		// IMonolingualClusteringAlgorithm forbids concurrent execution by contract.
		final List<Document> originalDocuments = documents;
		clusters = multilingualClustering.process(documents,
				new IMonolingualClusteringAlgorithm()
		{
			public List<Cluster> process(List<Document> documents, LanguageCode language)
			{
				KMedoidClusteringAlgorithm2.this.documents = documents;
				KMedoidClusteringAlgorithm2.this.process(language);
				return KMedoidClusteringAlgorithm2.this.clusters;
			}
		});
		documents = originalDocuments;
	}

	/**
	 * Perform clustering for a given language.
	 */
	protected void process(LanguageCode language)
	{
		// Preprocessing of documents
		final PreprocessingContext preprocessingContext = 
				preprocessingPipeline.preprocess(documents, null, language);

		// Add trivial AllLabels so that we can reuse the common TD matrix builder
		final int [] stemsMfow = preprocessingContext.allStems.mostFrequentOriginalWordIndex;
		final short [] wordsType = preprocessingContext.allWords.type;
		final IntArrayList featureIndices = new IntArrayList(stemsMfow.length);
		for (int i = 0; i < stemsMfow.length; i++)
		{
			final short flag = wordsType[stemsMfow[i]];
			if ((flag & (ITokenizer.TF_COMMON_WORD | ITokenizer.TF_QUERY_WORD | ITokenizer.TT_NUMERIC)) == 0)
			{
				featureIndices.add(stemsMfow[i]);
			}
		}
		preprocessingContext.allLabels.featureIndex = featureIndices.toArray();
		preprocessingContext.allLabels.firstPhraseIndex = -1;

		// Further processing only if there are words to process
		if (preprocessingContext.hasLabels())
		{
			// Term-document matrix building and reduction
			final VectorSpaceModelContext vsmContext = new VectorSpaceModelContext(
					preprocessingContext);

			matrixBuilder.buildTermDocumentMatrix(vsmContext);
			matrixBuilder.buildTermPhraseMatrix(vsmContext);

			final DoubleMatrix2D tdMatrix = vsmContext.termDocumentMatrix;
			
			cluster(vsmContext, preprocessingContext, tdMatrix);
			Collections.sort(clusters, Cluster.BY_REVERSED_SIZE_AND_LABEL_COMPARATOR);
		}
	}

	public IntArrayList cluster(VectorSpaceModelContext vsmContext, 
			PreprocessingContext preprocessingContext, DoubleMatrix2D tdMatrix) 
	{
		clusters = Lists.newArrayList();
		// Prepare rowIndex -> stemIndex mapping for labeling
		final IntIntHashMap rowToStemIndex = new IntIntHashMap();
		for (IntIntCursor c : vsmContext.stemToRowIndex)
		{
			rowToStemIndex.put(c.value, c.key);
		}

		final IntArrayList medoids = new IntArrayList(numClusters);
		final List<IntArrayList> rawClusters = Lists.newArrayList();
		for (int i = 0; i < numClusters; i++) {
			int random = rg.nextInt(tdMatrix.columns());
			medoids.add(random);
			rawClusters.add(new IntArrayList());
		}

		boolean changed = true;
		int count = 0;
		while (changed && count < maxIterations) {
			changed = false;
			count++;
			int[] assignment = assign(medoids, tdMatrix);
			changed = recalculateMedoids(assignment, medoids, rawClusters, tdMatrix);
		}

		for (int i = 0; i < rawClusters.size(); i++)
		{
			final Cluster cluster = new Cluster();

			final IntArrayList rawCluster = rawClusters.get(i);
			if (rawCluster.size() > 1)
			{
				cluster.addPhrases(getLabels(rawCluster,
						vsmContext.termDocumentMatrix, rowToStemIndex,
						preprocessingContext.allStems.mostFrequentOriginalWordIndex,
						preprocessingContext.allWords.image));


				int[] docsIndices = new int[rawCluster.size()]; 
				for (int j = 0; j < rawCluster.size(); j++)
				{
					cluster.addDocuments(documents.get(rawCluster.get(j)));
					docsIndices[j] = rawCluster.get(j);
					
				}
//				cluster.setAttribute("medoid", documents.get(medoids.get(i)));
				cluster.setAttribute("docIndices", docsIndices);
				clusters.add(cluster);
			}
		}

		double[][] pdist = new double[clusters.size()][clusters.size()];
		for (int i = 0; i < clusters.size(); i++){
			for(int j = i+1; j < clusters.size(); j++){
				pdist[i][j] = pdist[j][i] = dm.measure(tdMatrix.viewColumn(medoids.get(i)), tdMatrix.viewColumn(medoids.get(j)));
			}
			clusters.get(i).setAttribute("pdistance", pdist[i]);
		}

		return medoids;
	}

	private List<String> getLabels(IntArrayList documents,
			DoubleMatrix2D termDocumentMatrix, IntIntHashMap rowToStemIndex,
			int [] mostFrequentOriginalWordIndex, char [][] wordImage)
	{
		// Prepare a centroid. If dimensionality reduction was used,
		// the centroid from k-means will not be based on real terms,
		// so we need to calculate the centroid here once again based
		// on the cluster's documents.
		final DoubleMatrix1D centroid = new DenseDoubleMatrix1D(termDocumentMatrix.rows());
		for (IntCursor d : documents)
		{
			centroid.assign(termDocumentMatrix.viewColumn(d.value), Functions.PLUS);
		}

		final List<String> labels = Lists.newArrayListWithCapacity(labelCount);

		final int [] order = IndirectSort.mergesort(0, centroid.size(),
				new IndirectComparator()
		{
			@Override
			public int compare(int a, int b)
			{
				final double valueA = centroid.get(a);
				final double valueB = centroid.get(b);
				return valueA < valueB ? -1 : valueA > valueB ? 1 : 0;
			}
		});
		final double minValueForLabel = centroid.get(order[order.length
		                                                   - Math.min(labelCount, order.length)]);

		for (int i = 0; i < centroid.size(); i++)
		{
			if (centroid.getQuick(i) >= minValueForLabel)
			{
				labels.add(LabelFormatter.format(new char [] []
						{
					wordImage[mostFrequentOriginalWordIndex[rowToStemIndex.get(i)]]
						}, new boolean []
								{
										false
								}, false));
			}
		}
		return labels;
	}

	/**
	 * Assign all instances from the data set to the medoids.
	 * 
	 * @param medoids candidate medoids
	 * @param tdMatrix 
	 * @param data the data to assign to the medoids
	 * @return best cluster indices for each instance in the data set
	 */
	private int[] assign(IntArrayList medoids, DoubleMatrix2D tdMatrix) {
		int[] out = new int[tdMatrix.columns()];
		for (int i = 0; i < tdMatrix.columns(); i++) {
			double bestDistance = dm.measure(tdMatrix.viewColumn(i), tdMatrix.viewColumn(medoids.get(0)));
			int bestIndex = 0;
			for (int j = 1; j < medoids.size(); j++) {
				double tmpDistance = dm.measure(tdMatrix.viewColumn(i), tdMatrix.viewColumn(medoids.get(j)));
				if (dm.compare(tmpDistance, bestDistance)) {
					bestDistance = tmpDistance;
					bestIndex = j;
				}
			}
			out[i] = bestIndex;

		}
		return out;

	}

	/**
	 * Return a array with on each position the clusterIndex to which the
	 * Instance on that position in the dataset belongs.
	 * 
	 * @param medoids
	 *            the current set of cluster medoids, will be modified to fit
	 *            the new assignment
	 * @param assigment
	 *            the new assignment of all instances to the different medoids
	 * @param output
	 *            the cluster output, this will be modified at the end of the
	 *            method
	 * @return the
	 */
	private boolean recalculateMedoids(int[] assignment, IntArrayList medoids,
			List<IntArrayList> output, DoubleMatrix2D tdMatrix) {

		boolean changed = false;
		for (int i = 0; i < numClusters; i++) {
			output.set(i, new IntArrayList());
			for (int j = 0; j < assignment.length; j++) {
				if (assignment[j] == i) {
					output.get(i).add(j);
				}
			}
			if (output.get(i).size() == 0) { // new random, empty medoid
				medoids.set(i, rg.nextInt(tdMatrix.columns()));
				changed = true;
			} else {
				double[] centroid = getCentroid(output.get(i), tdMatrix);
				int oldMedoid = medoids.get(i);
				medoids.set(i, getClosest(tdMatrix, centroid));
				if (medoids.get(i) != (oldMedoid))
					changed = true;
			}
		}
		return changed;
	}

	/**
	 * Return the centroid of this cluster when using the given distance
	 * measure.
	 * 
	 * @param dm
	 *            the distance measure to use when calculating the centroid.
	 * @return the centroid of this dataset
	 */
	public double[] getCentroid(IntArrayList output, DoubleMatrix2D data) {
		if (data.size() == 0)
			return null;

		int instanceLength = data.viewColumn(0).size();
		double[] sumPosition = new double[instanceLength];
		for (int i = 0; i < data.columns(); i++) {
			for (int j = 0; j < data.rows(); j++) {
				sumPosition[j] +=  data.getQuick(j, i);
			}

		}
		for (int j = 0; j < instanceLength; j++) {
			sumPosition[j] /= data.columns();
		}
		return sumPosition;
	}

	/**
	 * Returns the instance of the given dataset that is closest to the instance
	 * that is given as a parameter.
	 * 
	 * @param data
	 *            the dataset to search in
	 * @param inst
	 *            the instance for which we need to find the closest
	 * @return
	 */
	public int getClosest(DoubleMatrix2D data, double[] centroid) {
		DoubleMatrix1D closest = data.viewColumn(0);
		DoubleMatrix1D inst = new SparseDoubleMatrix1D(centroid);
		int closestIndex = 0;
		double bestDistance = dm.measure(inst, closest);
		for (int i = 1; i < data.columns(); i++) {
			double tmpDistance = dm.measure(inst, data.viewColumn(i));
			if (dm.compare(tmpDistance, bestDistance)) {
				bestDistance = tmpDistance;
				closest = data.viewColumn(i);
				closestIndex = i;
			}
		}
		return closestIndex;
	}

}
