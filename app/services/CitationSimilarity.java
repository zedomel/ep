package services;

import java.io.IOException;

import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;

/**
 * Similarity class for compute document's score incorporating
 * number of citations.
 * 
 * @author jose
 *
 */
public class CitationSimilarity extends Similarity{
	
	/**
	 * Delegate similarity
	 */
	private final Similarity sim;

	/**
	 * Creates a new {@link CitationSimilarity}.
	 * @param sim delegate similarity.
	 */
	public CitationSimilarity(Similarity sim) {
		super();
		this.sim = sim;
	}

	@Override
	public long computeNorm(FieldInvertState state) {
		return sim.computeNorm(state);
	}

	@Override
	public SimWeight computeWeight(CollectionStatistics collectionStats, TermStatistics... termStats) {
		return sim.computeWeight(collectionStats, termStats);
	}

	/**
	 * Uses the number of citation as multiplication parameter of the method
	 * {@link SimScorer#score(int, float)}.
	 * 
	 */
	@Override
	public SimScorer simScorer(SimWeight stats, LeafReaderContext context) throws IOException {
		SimScorer sub = sim.simScorer(stats, context);
		final NumericDocValues values = context.reader().getNumericDocValues("citCount");
		
		return new SimScorer() {
			
			@Override
			public float score(int doc, float freq) {				
				return values.get(doc) * sub.score(doc, freq);
			}
			
			@Override
			public float computeSlopFactor(int distance) {
				return sub.computeSlopFactor(distance);
			}
			
			@Override
			public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
				return sub.computePayloadFactor(doc, start, end, payload);
			}
		};
	}
}