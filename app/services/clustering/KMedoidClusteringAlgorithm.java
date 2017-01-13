package services.clustering;

import java.util.Collections;
import java.util.List;

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
public class KMedoidClusteringAlgorithm extends ProcessingComponentBase {

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

	private int[] medoids;

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
				KMedoidClusteringAlgorithm.this.documents = documents;
				KMedoidClusteringAlgorithm.this.process(language);
				return KMedoidClusteringAlgorithm.this.clusters;
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

			// Calculate distance matrix
			DistanceMatrix distanceMatrix = new DistanceMatrix(tdMatrix, dm);
			
			cluster(vsmContext, preprocessingContext, distanceMatrix);
			Collections.sort(clusters, Cluster.BY_REVERSED_SIZE_AND_LABEL_COMPARATOR);
		}
	}

	public int[] cluster(VectorSpaceModelContext vsmContext, 
			PreprocessingContext preprocessingContext, DistanceMatrix distanceMatrix) 
	{
		clusters = Lists.newArrayList();
		// Prepare rowIndex -> stemIndex mapping for labeling
		final IntIntHashMap rowToStemIndex = new IntIntHashMap();
		for (IntIntCursor c : vsmContext.stemToRowIndex)
		{
			rowToStemIndex.put(c.value, c.key);
		}
		
		final List<IntArrayList> rawClusters = Lists.newArrayList();
		for (int i = 0; i < numClusters; i++)
			rawClusters.add(new IntArrayList());

		int[] oldMedoids = new int[numClusters];
		this.medoids = new int[numClusters];

		for(int i = 0; i < numClusters; i++)
			medoids[i] = (i * (distanceMatrix.getElementCount() / numClusters));

		boolean changed = true;
		int count = 0;
		

		while (changed && count < distanceMatrix.getElementCount()) 
		{
			int nrElements = Integer.MAX_VALUE;

			for (int point = 0; point < distanceMatrix.getElementCount(); point++){
				int nearestCluster = 0;
				double distance = distanceMatrix.getDistance(point, medoids[nearestCluster]);

				for(int cluster = 1; cluster < rawClusters.size(); cluster++){
					double distance2 = distanceMatrix.getDistance(point, medoids[cluster]);
					
					if (distance > distance2){
						nearestCluster = cluster;
						distance = distance2;
					}
					else if ( (distance == distance2) && 
							nrElements > rawClusters.get(cluster).size()){
						nrElements = rawClusters.get(cluster).size() + 1;
						nearestCluster = cluster;
						distance = distance2;
					}
				}

				rawClusters.get(nearestCluster).add(point);
			}

			oldMedoids = medoids;

			updateMedoids(distanceMatrix, rawClusters);
			changed = isMedoidModified(oldMedoids);
			count++;
		}

		for (int i = 0; i < rawClusters.size(); i++)
		{
			final Cluster cluster = new Cluster();
			
			final IntArrayList rawCluster = rawClusters.get(i);
			if (rawCluster.size() > 0)
			{
				cluster.addPhrases(getLabels(rawCluster,
						vsmContext.termDocumentMatrix, rowToStemIndex,
						preprocessingContext.allStems.mostFrequentOriginalWordIndex,
						preprocessingContext.allWords.image));


				int[] docsIndices = new int[rawCluster.size()]; 
				for (int j = 0; j < rawCluster.size(); j++)
				{
					cluster.addDocument(documents.get(rawCluster.get(j)));
					docsIndices[j] = rawCluster.get(j);

				}
				cluster.setAttribute("docIndices", docsIndices);
				clusters.add(cluster);
			}
		}

		return medoids;
	}

	private void updateMedoids(DistanceMatrix distanceMatrix, List<IntArrayList> clusters) {
		for (int cluster = 0; cluster < clusters.size(); cluster++){
			int medoid = clusters.get(cluster).get(0);
			double sumDistances = distanceMatrix.getMaxDistance();

			for( int point = 0; point < clusters.get(cluster).size(); point++){
				double sumDistances2 = 0.0;

				for( int point2 = 0; point2 < clusters.get(cluster).size(); point2++)
					sumDistances2 += distanceMatrix.getDistance(
							clusters.get(cluster).get(point), clusters.get(cluster).get(point2));
				sumDistances2 /= clusters.get(cluster).size();

				if (sumDistances > sumDistances2){
					sumDistances = sumDistances2;
					medoid = clusters.get(cluster).get(point);
				}
			}
			medoids[cluster] = medoid;
		}
	}

	private boolean isMedoidModified(int[] oldMedoids) {
		for (int medoid = 0; medoid < oldMedoids.length; medoid++) {
			if (oldMedoids[medoid] != this.medoids[medoid]) {
				return true;
			}
		}
		return false;
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
}
