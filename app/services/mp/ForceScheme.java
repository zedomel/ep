package services.mp;

import java.util.ArrayList;

import services.clustering.DistanceMatrix;
import services.clustering.DistanceMeasure;

public class ForceScheme
{
	private float fractionDelta;
	private int[] index;
	private static final float EPSILON = 1.0E-7F;

	public ForceScheme(float fractionDelta, int numberPoints)
	{
		this.fractionDelta = fractionDelta;


		ArrayList<Integer> index_aux = new ArrayList<>();
		for (int i = 0; i < numberPoints; i++) {
			index_aux.add(i);
		}

		this.index = new int[numberPoints];
		int ind = 0; 
		for (int j = 0; j < this.index.length; j++) {
			if (ind >= index_aux.size()) {
				ind = 0;
			}

			this.index[j] = index_aux.get(ind).intValue();
			index_aux.remove(ind);
			ind += index_aux.size() / 10;
		}
	}

	public float iteration(DistanceMatrix dmat, double[][] projection)
	{
		float error = 0.0F;

		if (projection[0].length == 2)
		{
			for (int ins1 = 0; ins1 < projection.length; ins1++) {
				int instance = this.index[ins1];

				for (int ins2 = 0; ins2 < projection.length; ins2++) {
					int instance2 = this.index[ins2];

					if (instance != instance2)
					{
						double x1x2 = projection[instance2][0] - projection[instance][0];
						double y1y2 = projection[instance2][1] - projection[instance][1];
						double dr2 = Math.sqrt(x1x2 * x1x2 + y1y2 * y1y2);

						if (dr2 < EPSILON) {
							dr2 = EPSILON;
						}

						double drn = dmat.getDistance(instance, instance2);
						double normdrn = (drn - dmat.getMinDistance()) / (dmat.getMaxDistance() - dmat.getMinDistance());

						double delta = normdrn - dr2;
						delta *= Math.abs(delta);

						delta /= this.fractionDelta;

						error += Math.abs(delta);

						projection[instance2][0] += delta * (x1x2 / dr2);
						projection[instance2][1] += delta * (y1y2 / dr2);
					}
				}
			}
			error /= (projection.length * projection.length - projection.length);
		} else if (projection[0].length == 3)
		{
			for (int ins1 = 0; ins1 < projection.length; ins1++) {
				int instance = this.index[ins1];

				for (int ins2 = 0; ins2 < projection.length; ins2++) {
					int instance2 = this.index[ins2];

					if (instance != instance2)
					{
						double x1x2 = projection[instance2][0] - projection[instance][0];
						double y1y2 = projection[instance2][1] - projection[instance][1];
						double z1z2 = projection[instance2][2] - projection[instance][2];

						double dr3 = Math.sqrt(x1x2 * x1x2 + y1y2 * y1y2 + z1z2 * z1z2);

						if (dr3 < EPSILON) {
							dr3 = EPSILON;
						}

						double drn = dmat.getDistance(instance, instance2);
						double normdrn = (drn - dmat.getMinDistance()) / (dmat.getMaxDistance() - dmat.getMinDistance());

						double delta = normdrn - dr3;
						delta *= Math.abs(delta);

						delta /= this.fractionDelta;

						error += Math.abs(delta);

						projection[instance2][0] += delta * (x1x2 / dr3);
						projection[instance2][1] += delta * (y1y2 / dr3);
						projection[instance2][2] += delta * (z1z2 / dr3);
					}
				}
			}
			error /= (projection.length * projection.length - projection.length);
		}

		return error;
	}

	public float iteration(DistanceMatrix matrix, DistanceMeasure diss, float[][] projection) {
		float error = 0.0F;

		if (projection[0].length == 2)
		{
			for (int ins1 = 0; ins1 < projection.length; ins1++) {
				int instance = this.index[ins1];


				for (int ins2 = 0; ins2 < projection.length; ins2++) {
					int instance2 = this.index[ins2];

					if (instance != instance2)
					{
						float x1x2 = projection[instance2][0] - projection[instance][0];
						float y1y2 = projection[instance2][1] - projection[instance][1];
						float dr2 = (float)Math.sqrt(x1x2 * x1x2 + y1y2 * y1y2);

						if (dr2 < EPSILON) {
							dr2 = EPSILON;
						}

						double drn = diss.measure(matrix.viewRow(instance), matrix.viewRow(instance2));
						double normdrn = drn / 5.0F;
						double delta = normdrn - dr2;
						delta *= Math.abs(delta);

						delta /= this.fractionDelta;

						error += Math.abs(delta);


						projection[instance2][0] += delta * (x1x2 / dr2);
						projection[instance2][1] += delta * (y1y2 / dr2);
					}
				}
			}
			error /= (projection.length * projection.length - projection.length);
		} else if (projection[0].length == 3)
		{
			for (int ins1 = 0; ins1 < projection.length; ins1++) {
				int instance = this.index[ins1];


				for (int ins2 = 0; ins2 < projection.length; ins2++) {
					int instance2 = this.index[ins2];

					if (instance != instance2)
					{
						float x1x2 = projection[instance2][0] - projection[instance][0];
						float y1y2 = projection[instance2][1] - projection[instance][1];
						float z1z2 = projection[instance2][2] - projection[instance][2];

						float dr3 = (float)Math.sqrt(x1x2 * x1x2 + y1y2 * y1y2 + z1z2 * z1z2);

						if (dr3 < EPSILON) {
							dr3 = EPSILON;
						}

						double drn = diss.measure(matrix.viewRow(instance), matrix.viewRow(instance2));
						double normdrn = drn / 5.0F;
						double delta = normdrn - dr3;
						delta *= Math.abs(delta);

						delta /= this.fractionDelta;

						error += Math.abs(delta);

						projection[instance2][0] += delta * (x1x2 / dr3);
						projection[instance2][1] += delta * (y1y2 / dr3);
						projection[instance2][2] += delta * (z1z2 / dr3);
					}
				}
			}
		}
		return error;
	}
}