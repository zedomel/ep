//package services.clustering;
//
//import java.util.List;
//
//import org.carrot2.core.Cluster;
//import org.carrot2.core.Document;
//import org.carrot2.core.LanguageCode;
//import org.carrot2.core.ProcessingComponentBase;
//import org.carrot2.core.ProcessingException;
//import org.carrot2.core.attribute.AttributeNames;
//import org.carrot2.core.attribute.CommonAttributes;
//import org.carrot2.core.attribute.Init;
//import org.carrot2.core.attribute.Internal;
//import org.carrot2.core.attribute.Processing;
//import org.carrot2.mahout.math.matrix.DoubleMatrix2D;
//import org.carrot2.text.analysis.ITokenizer;
//import org.carrot2.text.clustering.IMonolingualClusteringAlgorithm;
//import org.carrot2.text.clustering.MultilingualClustering;
//import org.carrot2.text.preprocessing.LabelFormatter;
//import org.carrot2.text.preprocessing.PreprocessingContext;
//import org.carrot2.text.preprocessing.pipeline.BasicPreprocessingPipeline;
//import org.carrot2.text.preprocessing.pipeline.IPreprocessingPipeline;
//import org.carrot2.text.vsm.TermDocumentMatrixBuilder;
//import org.carrot2.text.vsm.TermDocumentMatrixReducer;
//import org.carrot2.text.vsm.VectorSpaceModelContext;
//import org.carrot2.util.attribute.Attribute;
//import org.carrot2.util.attribute.AttributeLevel;
//import org.carrot2.util.attribute.Bindable;
//import org.carrot2.util.attribute.DefaultGroups;
//import org.carrot2.util.attribute.Group;
//import org.carrot2.util.attribute.Input;
//import org.carrot2.util.attribute.Label;
//import org.carrot2.util.attribute.Level;
//import org.carrot2.util.attribute.Output;
//import org.carrot2.util.attribute.Required;
//import org.carrot2.util.attribute.constraint.ImplementingClasses;
//import org.carrot2.util.attribute.constraint.IntRange;
//
//import com.carrotsearch.hppc.IntArrayList;
//
//import cern.colt.matrix.impl.DenseDoubleMatrix2D;
//import mdsj.MDSJ;
//import services.mp.MultidimensionalProjection;
//
//@Bindable(prefix = "SearchProcessing", inherit = CommonAttributes.class)
//public class SearchProcessing2 extends ProcessingComponentBase {
//
//	public static final String NUM_NEIGHBORS = "num_neighbors";
//
//	@Processing
//	@Input
//	@Required
//	@Internal
//	@Attribute(key = AttributeNames.DOCUMENTS, inherit = true)
//	public List<Document> documents;
//
//	@Processing
//	@Output
//	@Internal
//	@Attribute(key = AttributeNames.CLUSTERS, inherit = true)
//	public List<Cluster> clusters = null;
//
//	/**
//	 * The number of clusters to create. The algorithm will create at most the specified
//	 * number of clusters.
//	 */
//	@Processing
//	@Input
//	@Attribute(key = KMedoidClusteringAlgorithm.NUM_CLUSTERS)
//	@IntRange(min = 1)
//	@Group(DefaultGroups.CLUSTERS)
//	@Level(AttributeLevel.BASIC)
//	@Label("Cluster count")
//	public int numClusters = 2;
//
//	/**
//	 * The maximum number of k-means iterations to perform.
//	 */
//	@Processing
//	@Input
//	@Attribute(key = KMedoidClusteringAlgorithm.MAX_ITERATIONS)
//	@IntRange(min = 1)
//	@Level(AttributeLevel.BASIC)
//	@Label("Maximum iterations")
//	public int maxIterations = 15;
//
//	/**
//	 * Label count. The minimum number of labels to return for each cluster.
//	 */
//	@Processing
//	@Input
//	@Attribute
//	@IntRange(min = 1, max = 10)
//	@Group(DefaultGroups.CLUSTERS)
//	@Level(AttributeLevel.BASIC)
//	@Label("Label count")
//	public int labelCount = 3;
//
//	/**
//	 * Common preprocessing tasks handler.
//	 */
//	@Init
//	@Input
//	@Attribute
//	@Internal
//	@ImplementingClasses(classes = {}, strict = false)
//	@Level(AttributeLevel.ADVANCED)
//	public IPreprocessingPipeline preprocessingPipeline = new BasicPreprocessingPipeline();
//	
//	@Processing
//	@Input
//	@Attribute(key = KMedoidClusteringAlgorithm.DISTANCE_MEASURE)
//	@ImplementingClasses(classes = {EuclideanDistance.class}, strict = false)
//	public DistanceMeasure dm = new EuclideanDistance();
//
//	@Processing
//	@Input
//	@Attribute(key = NUM_NEIGHBORS)
//	@IntRange(min = 1)
//	@Level(AttributeLevel.BASIC)
//	@Label("Number of neighbors")
//	private int numNeighbors;
//
//	/**
//	 * Term-document matrix builder for the algorithm, contains bindable attributes.
//	 */
//	public final TermDocumentMatrixBuilder matrixBuilder = new TermDocumentMatrixBuilder();
//
//	/**
//	 * Term-document matrix reducer for the algorithm, contains bindable attributes.
//	 */
//	public final TermDocumentMatrixReducer matrixReducer = new TermDocumentMatrixReducer();
//
//	/**
//	 * Cluster label formatter, contains bindable attributes.
//	 */
//	public final LabelFormatter labelFormatter = new LabelFormatter();
//
//	/**
//	 * A helper for performing multilingual clustering.
//	 */
//	public final MultilingualClustering multilingualClustering = new MultilingualClustering();
//
//	@Override
//	public void process() throws ProcessingException
//	{
//		// There is a tiny trick here to support multilingual clustering without
//		// refactoring the whole component: we remember the original list of documents
//		// and invoke clustering for each language separately within the 
//		// IMonolingualClusteringAlgorithm implementation below. This is safe because
//		// processing components are not thread-safe by definition and 
//		// IMonolingualClusteringAlgorithm forbids concurrent execution by contract.
//		final List<Document> originalDocuments = documents;
//		clusters = multilingualClustering.process(documents,
//				new IMonolingualClusteringAlgorithm()
//		{
//			public List<Cluster> process(List<Document> documents, LanguageCode language)
//			{
//				SearchProcessing2.this.documents = documents;
//				SearchProcessing2.this.process(language);
//				return SearchProcessing2.this.clusters;
//			}
//		});
//		documents = originalDocuments;
//	}
//
//	/**
//	 * Perform clustering for a given language.
//	 */
//	protected void process(LanguageCode language)
//	{
//		// Preprocessing of documents
//		final PreprocessingContext preprocessingContext = 
//				preprocessingPipeline.preprocess(documents, null, language);
//
//		// Add trivial AllLabels so that we can reuse the common TD matrix builder
//		final int [] stemsMfow = preprocessingContext.allStems.mostFrequentOriginalWordIndex;
//		final short [] wordsType = preprocessingContext.allWords.type;
//		final IntArrayList featureIndices = new IntArrayList(stemsMfow.length);
//		for (int i = 0; i < stemsMfow.length; i++)
//		{
//			final short flag = wordsType[stemsMfow[i]];
//			if ((flag & (ITokenizer.TF_COMMON_WORD | ITokenizer.TF_QUERY_WORD | ITokenizer.TT_NUMERIC)) == 0)
//			{
//				featureIndices.add(stemsMfow[i]);
//			}
//		}
//		preprocessingContext.allLabels.featureIndex = featureIndices.toArray();
//		preprocessingContext.allLabels.firstPhraseIndex = -1;
//
//		// Further processing only if there are words to process
//		if (preprocessingContext.hasLabels())
//		{
//			// Term-document matrix building and reduction
//			final VectorSpaceModelContext vsmContext = new VectorSpaceModelContext(
//					preprocessingContext);
//
//			matrixBuilder.buildTermDocumentMatrix(vsmContext);
//			matrixBuilder.buildTermPhraseMatrix(vsmContext);
//
//			final DoubleMatrix2D tdMatrix = vsmContext.termDocumentMatrix;
//			
//			KMedoidClusteringAlgorithm pam = new KMedoidClusteringAlgorithm();
//			pam.dm = dm;
//			pam.documents = documents;
//			pam.labelCount = labelCount;
//			pam.maxIterations = maxIterations;
//			pam.numClusters = numClusters;
//			IntArrayList medoids = pam.cluster(vsmContext, preprocessingContext, tdMatrix);
//			clusters = pam.clusters;
//			
//			DoubleMatrix2D projection = multidimensionalProjection(tdMatrix, medoids);
//			
//			for(int i = 0; i < clusters.size(); i++){
//				Cluster cluster = clusters.get(i);
//				int[] docsIndices = cluster.getAttribute("docsIndices");
//				if (docsIndices != null){
//					double[][] coordinates = new double[cluster.size()][2];
//					for(int j = 0; j < docsIndices.length; j++){
//						coordinates[j] = projection.viewRow( docsIndices[j] ).toArray();
//					}
//					cluster.setAttribute("coordinates", coordinates);
//				}
//			}
//		}
//	}
//
//	private DoubleMatrix2D multidimensionalProjection(DoubleMatrix2D tdMatrix, IntArrayList medoids) {
//		
//		double[][] d = new double[medoids.size()][medoids.size()];
//		for(int i = 0; i < medoids.size(); i++){
//			for(int j = i + 1; j < medoids.size(); j++){
//				d[i][j] = d[j][i] = dm.measure(tdMatrix.viewColumn(medoids.get(i)), tdMatrix.viewColumn(medoids.get(j)));
//			}
//		}
//		
//		cern.colt.matrix.DoubleMatrix2D controlPoints = new DenseDoubleMatrix2D( MDSJ.classicalScaling(d, 2) );
//		cern.colt.matrix.DoubleMatrix2D points = new DenseDoubleMatrix2D(tdMatrix.toArray());
//		
//		MultidimensionalProjection mp = new MultidimensionalProjection();
//		int[] controlPointsIndices = new int[clusters.size()];
//		
//		cern.colt.matrix.DoubleMatrix2D projection = mp.project(points.viewDice(), controlPointsIndices, 
//				controlPoints.viewDice(), 2, numNeighbors);
//		
//		return new org.carrot2.mahout.math.matrix.impl.DenseDoubleMatrix2D(projection.toArray());
//	}
//}
