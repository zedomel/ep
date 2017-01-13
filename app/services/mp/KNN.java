package services.mp;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import cern.colt.matrix.DoubleMatrix2D;
import services.clustering.DistanceMatrix;
import services.clustering.DistanceMeasure;

public class KNN
{
	public KNN(int nrNeighbors)
	{
		this.nrNeighbors = nrNeighbors;
	}

	public Pair[][] execute(DoubleMatrix2D matrix, DistanceMeasure diss) throws IOException {
		DistanceMatrix dmat = new DistanceMatrix(matrix, diss);
		return execute(dmat);
	}

	public Pair[][] execute(DistanceMatrix dmat) throws IOException {
		long start = System.currentTimeMillis();
		Pair[][] neighbors = null;

		if (this.nrNeighbors > dmat.getElementCount() - 1) {
			throw new IOException("Number of neighbors bigger than the number of elements minus one (an element is not computed as a neighbor of itself)!");
		}

		neighbors = new Pair[dmat.getElementCount()][];
		for (int i = 0; i < neighbors.length; i++) {
			neighbors[i] = new Pair[this.nrNeighbors];

			for (int j = 0; j < neighbors[i].length; j++) {
				neighbors[i][j] = new Pair(-1, Float.MAX_VALUE);
			}
		}

		for (int i = 0; i < dmat.getElementCount(); i++) {
			for (int j = 0; j < dmat.getElementCount(); j++) {
				if (i != j)
				{
					double dist = dmat.getDistance(i, j);

					if (dist < neighbors[i][(neighbors[i].length - 1)].value) {
						for (int k = 0; k < neighbors[i].length; k++) {
							if (neighbors[i][k].value > dist) {
								for (int n = neighbors[i].length - 1; n > k; n--) {
									neighbors[i][n].index = neighbors[i][(n - 1)].index;
									neighbors[i][n].value = neighbors[i][(n - 1)].value;
								}

								neighbors[i][k].index = j;
								neighbors[i][k].value = dist;
								break;
							}
						}
					}
				}
			}
		}
		long finish = System.currentTimeMillis();

		Logger.getLogger(getClass().getName()).log(Level.INFO, "KNN time: " + (float)(finish - start) / 1000.0F + "s");


		return neighbors;
	}

	private int nrNeighbors = 5;
}