package services.mp;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import cern.colt.matrix.DoubleMatrix2D;
import services.clustering.DistanceMatrix;
import services.clustering.DistanceMeasure;

public class MeshGenerator
{
	
	public MeshGenerator() {
	
	}
	
	public Pair[][] execute(Pair[][] neighbors, DistanceMatrix dmat)
	{
		HashSet<Integer> visited = new HashSet<>();
		HashSet<Integer> tovisit = new HashSet<>();
		HashSet<Integer> notvisited = new HashSet<>();

		tovisit.add(0);
		for (int i = 1; i < neighbors.length; i++) {
			notvisited.add(i);
		}

		while (notvisited.size() > 0) {
			if (tovisit.size() > 0) {
				int next = tovisit.iterator().next();
				visited.add(next);
				tovisit.remove(next);
				notvisited.remove(next);

				for (int i = 0; i < neighbors[next].length; i++) {
					if (!visited.contains(neighbors[next][i].index)) {
						tovisit.add(neighbors[next][i].index);
					}
				}
			} else {
				int next = notvisited.iterator().next();
				notvisited.remove(next);
				tovisit.add(next);

				Iterator<Integer> visited_it = visited.iterator();

				int closest = 0;
				double min = Float.MAX_VALUE;

				while (visited_it.hasNext()) {
					int aux = visited_it.next();
					double distance = dmat.getDistance(aux, next);

					if (min > distance) {
						min = distance;
						closest = aux;
					}
				}

				Pair[] newNeighbors1 = new Pair[neighbors[next].length + 1];
				for (int i = 0; i < neighbors[next].length; i++) {
					newNeighbors1[i] = neighbors[next][i];
				}

				newNeighbors1[neighbors[next].length] = new Pair(closest, min);
				neighbors[next] = newNeighbors1;

				Pair[] newNeighbors2 = new Pair[neighbors[closest].length + 1];
				for (int i = 0; i < neighbors[closest].length; i++) {
					newNeighbors2[i] = neighbors[closest][i];
				}

				newNeighbors2[neighbors[closest].length] = new Pair(next, min);
				neighbors[closest] = newNeighbors2;
			}
		}

		return neighbors;
	}

	public Pair[][] execute(Pair[][] neighbors, DoubleMatrix2D matrix, DistanceMeasure diss) throws IOException {

		TreeSet<Integer> visited = new TreeSet<>();
		TreeSet<Integer> tovisit = new TreeSet<>();
		TreeSet<Integer> notvisited = new TreeSet<>();

		tovisit.add(0);
		for (int i = 1; i < neighbors.length; i++) {
			notvisited.add(i);
		}

		while (notvisited.size() > 0) {
			if (tovisit.size() > 0) {
				int next = tovisit.first();
				visited.add(next);
				tovisit.remove(next);
				notvisited.remove(next);

				for (int i = 0; i < neighbors[next].length; i++) {
					if (!visited.contains(neighbors[next][i].index)) {
						tovisit.add(neighbors[next][i].index);
					}
				}
			} else {
				int next = notvisited.first();
				notvisited.remove(next);
				tovisit.add(next);

				Iterator<Integer> visited_it = visited.iterator();

				int closest = 0;
				double min = Float.MAX_VALUE;

				while (visited_it.hasNext()) {
					int aux = visited_it.next();
					double distance = diss.measure(matrix.viewRow(aux), matrix.viewRow(next));

					if (min > distance) {
						min = distance;
						closest = aux;
					}
				}

				Pair[] newNeighbors1 = new Pair[neighbors[next].length + 1];
				for (int i = 0; i < neighbors[next].length; i++) {
					newNeighbors1[i] = neighbors[next][i];
				}
				newNeighbors1[neighbors[next].length] = new Pair(closest, min);
				neighbors[next] = newNeighbors1;

				Pair[] newNeighbors2 = new Pair[neighbors[closest].length + 1];
				for (int i = 0; i < neighbors[closest].length; i++) {
					newNeighbors2[i] = neighbors[closest][i];
				}
				newNeighbors2[neighbors[closest].length] = new Pair(next, min);
				neighbors[closest] = newNeighbors2;
			}
		}

		return neighbors;
	}
}
