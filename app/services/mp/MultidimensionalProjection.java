package services.mp;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import cern.colt.matrix.linalg.CholeskyDecomposition;
import services.clustering.DistanceMatrix;
import services.clustering.EuclideanDistance;
import services.clustering.KMedoidClusteringAlgorithm;


public class MultidimensionalProjection {

	private int numberIterations = 50;
	private int numberOfNeighbors = 2;
	private float delta = 0.8f;
	private int[][] neighbors;

	public MultidimensionalProjection() {
		
	}
	
	public MultidimensionalProjection(int maxIterations, int numberOfNeighbors, float delta) {
		this.numberIterations = maxIterations;
		this.numberOfNeighbors = numberOfNeighbors;
		this.delta = delta;
	}
	
	public MultidimensionalProjection(int maxIterations, int numberOfNeighbors) {
		this(maxIterations, numberOfNeighbors, 0.8f);
	}

	public double[][] project(DistanceMatrix distanceMatrix, int[] controlPoints)
	{
		final int nrCp = controlPoints.length; 
		DistanceMatrix dmatCP = new DistanceMatrix(nrCp);

		for (int i = 0; i < nrCp; i++) {
			for (int j = 0; j < nrCp; j++) {
				if (i != j) {
					dmatCP.setDistance(i, j, distanceMatrix.getDistance(controlPoints[i], controlPoints[j]));
				}
			}
		}
		
		try {
			NearestNeighborProjection projector = new NearestNeighborProjection();
			double[][] projectionCP = projector.project(dmatCP);

			if (projectionCP != null) {
				ForceScheme force = new ForceScheme(delta , projectionCP.length);
				for (int i = 0; i < numberIterations ; i++) {
					force.iteration(dmatCP, projectionCP);
				}
			}

			KNN knnmesh = new KNN(numberOfNeighbors );
			Pair[][] mesh = knnmesh.execute(distanceMatrix);
			
			//Compute neighbors
			neighbors = new int[mesh.length][numberOfNeighbors];
			for(int i = 0; i < mesh.length; i++){
				for(int j = 0; j < numberOfNeighbors; j++)
					neighbors[i][j] = mesh[i][j].index;
			}
			
			return createFinalProjection(mesh, distanceMatrix, controlPoints, projectionCP);
		}catch (Exception e) {
			Logger.getLogger(MultidimensionalProjection.class.getName()).log(Level.SEVERE, null, e);
		}

		return null;
	}
	
	public int[][] getNeighbors(){
		return neighbors;
	}

	private double[][] createFinalProjection(Pair[][] neighbors, DistanceMatrix dmat, int[] controlPoints, double[][] projectionCP)
	{
		double[][] projection = new double[dmat.getElementCount()][];
		projectUsingColt(neighbors, projection, controlPoints, projectionCP);
		Runtime.getRuntime().gc();

		return projection;
	}

	private void projectUsingColt(Pair[][] neighbors, double[][] projection, int[] controlPoints, double[][] projectionCP) {
		long start = System.currentTimeMillis();

		int nRows = neighbors.length + controlPoints.length;
		;
		int nColumns = neighbors.length;
		SparseDoubleMatrix2D A = new SparseDoubleMatrix2D(nRows, nColumns);

		for (int i = 0; i < neighbors.length; i++)
		{
			A.setQuick(i, i, 1.0D);

			double max = Float.NEGATIVE_INFINITY;
			double min = Float.POSITIVE_INFINITY;

			for (int j = 0; j < neighbors[i].length; j++) {
				if (max < neighbors[i][j].value) {
					max = neighbors[i][j].value;
				}

				if (min > neighbors[i][j].value) {
					min = neighbors[i][j].value;
				}
			}

			float sum = 0.0F;
			for (int j = 0; j < neighbors[i].length; j++) {
				if (max > min) {
					double dist = (neighbors[i][j].value - min) / (max - min) * 0.9F + 0.1F;
					sum += 1.0F / dist;
				}
			}

			for (int j = 0; j < neighbors[i].length; j++) {
				if (max > min) {
					double dist = (neighbors[i][j].value - min) / (max - min) * 0.9F + 0.1F;
					A.setQuick(i, neighbors[i][j].index, -(1.0F / dist / sum));
				} else {
					A.setQuick(i, neighbors[i][j].index, -(1.0F / neighbors[i].length));
				}
			}
		}


		for (int i = 0; i < controlPoints.length; i++) {
			A.setQuick(projection.length + i, controlPoints[i], 1.0D);
		}

		SparseDoubleMatrix2D B = new SparseDoubleMatrix2D(nRows, 2);
		for (int i = 0; i < projectionCP.length; i++) {
			B.setQuick(neighbors.length + i, 0, projectionCP[i][0]);
			B.setQuick(neighbors.length + i, 1, projectionCP[i][1]);
		}

		DoubleMatrix2D AtA = A.zMult(A, null, 1.0D, 1.0D, true, false);
		DoubleMatrix2D AtB = A.zMult(B, null, 1.0D, 1.0D, true, false);

		start = System.currentTimeMillis();
		CholeskyDecomposition chol = new CholeskyDecomposition(AtA);
		DoubleMatrix2D X = chol.solve(AtB);

		for (int i = 0; i < X.rows(); i++) {
			projection[i] = new double[2];
			projection[i][0] = ((float)X.getQuick(i, 0));
			projection[i][1] = ((float)X.getQuick(i, 1));
		}

		long finish = System.currentTimeMillis();

		Logger.getLogger(getClass().getName()).log(Level.INFO, "Solving the system using Colt time: " + (float)(finish - start) / 1000.0F + "s");
	}

	public static void main(String[] args) throws IOException {
		double[][] data = new double[][]{
			{5.1,3.5,1.4,0.2},
			{4.9,3.0,1.4,0.2},
			{4.7,3.2,1.3,0.2},
			{4.6,3.1,1.5,0.2},
			{5.0,3.6,1.4,0.2},
			{5.4,3.9,1.7,0.4},
			{4.6,3.4,1.4,0.3},
			{5.0,3.4,1.5,0.2},
			{4.4,2.9,1.4,0.2},
			{4.9,3.1,1.5,0.1},
			{5.4,3.7,1.5,0.2},
			{4.8,3.4,1.6,0.2},
			{4.8,3.0,1.4,0.1},
			{4.3,3.0,1.1,0.1},
			{5.8,4.0,1.2,0.2},
			{5.7,4.4,1.5,0.4},
			{5.4,3.9,1.3,0.4},
			{5.1,3.5,1.4,0.3},
			{5.7,3.8,1.7,0.3},
			{5.1,3.8,1.5,0.3},
			{5.4,3.4,1.7,0.2},
			{5.1,3.7,1.5,0.4},
			{4.6,3.6,1.0,0.2},
			{5.1,3.3,1.7,0.5},
			{4.8,3.4,1.9,0.2},
			{5.0,3.0,1.6,0.2},
			{5.0,3.4,1.6,0.4},
			{5.2,3.5,1.5,0.2},
			{5.2,3.4,1.4,0.2},
			{4.7,3.2,1.6,0.2},
			{4.8,3.1,1.6,0.2},
			{5.4,3.4,1.5,0.4},
			{5.2,4.1,1.5,0.1},
			{5.5,4.2,1.4,0.2},
			{4.9,3.1,1.5,0.2},
			{5.0,3.2,1.2,0.2},
			{5.5,3.5,1.3,0.2},
			{4.9,3.6,1.4,0.1},
			{4.4,3.0,1.3,0.2},
			{5.1,3.4,1.5,0.2},
			{5.0,3.5,1.3,0.3},
			{4.5,2.3,1.3,0.3},
			{4.4,3.2,1.3,0.2},
			{5.0,3.5,1.6,0.6},
			{5.1,3.8,1.9,0.4},
			{4.8,3.0,1.4,0.3},
			{5.1,3.8,1.6,0.2},
			{4.6,3.2,1.4,0.2},
			{5.3,3.7,1.5,0.2},
			{5.0,3.3,1.4,0.2},
			{7.0,3.2,4.7,1.4},
			{6.4,3.2,4.5,1.5},
			{6.9,3.1,4.9,1.5},
			{5.5,2.3,4.0,1.3},
			{6.5,2.8,4.6,1.5},
			{5.7,2.8,4.5,1.3},
			{6.3,3.3,4.7,1.6},
			{4.9,2.4,3.3,1.0},
			{6.6,2.9,4.6,1.3},
			{5.2,2.7,3.9,1.4},
			{5.0,2.0,3.5,1.0},
			{5.9,3.0,4.2,1.5},
			{6.0,2.2,4.0,1.0},
			{6.1,2.9,4.7,1.4},
			{5.6,2.9,3.6,1.3},
			{6.7,3.1,4.4,1.4},
			{5.6,3.0,4.5,1.5},
			{5.8,2.7,4.1,1.0},
			{6.2,2.2,4.5,1.5},
			{5.6,2.5,3.9,1.1},
			{5.9,3.2,4.8,1.8},
			{6.1,2.8,4.0,1.3},
			{6.3,2.5,4.9,1.5},
			{6.1,2.8,4.7,1.2},
			{6.4,2.9,4.3,1.3},
			{6.6,3.0,4.4,1.4},
			{6.8,2.8,4.8,1.4},
			{6.7,3.0,5.0,1.7},
			{6.0,2.9,4.5,1.5},
			{5.7,2.6,3.5,1.0},
			{5.5,2.4,3.8,1.1},
			{5.5,2.4,3.7,1.0},
			{5.8,2.7,3.9,1.2},
			{6.0,2.7,5.1,1.6},
			{5.4,3.0,4.5,1.5},
			{6.0,3.4,4.5,1.6},
			{6.7,3.1,4.7,1.5},
			{6.3,2.3,4.4,1.3},
			{5.6,3.0,4.1,1.3},
			{5.5,2.5,4.0,1.3},
			{5.5,2.6,4.4,1.2},
			{6.1,3.0,4.6,1.4},
			{5.8,2.6,4.0,1.2},
			{5.0,2.3,3.3,1.0},
			{5.6,2.7,4.2,1.3},
			{5.7,3.0,4.2,1.2},
			{5.7,2.9,4.2,1.3},
			{6.2,2.9,4.3,1.3},
			{5.1,2.5,3.0,1.1},
			{5.7,2.8,4.1,1.3},
			{6.3,3.3,6.0,2.5},
			{5.8,2.7,5.1,1.9},
			{7.1,3.0,5.9,2.1},
			{6.3,2.9,5.6,1.8},
			{6.5,3.0,5.8,2.2},
			{7.6,3.0,6.6,2.1},
			{4.9,2.5,4.5,1.7},
			{7.3,2.9,6.3,1.8},
			{6.7,2.5,5.8,1.8},
			{7.2,3.6,6.1,2.5},
			{6.5,3.2,5.1,2.0},
			{6.4,2.7,5.3,1.9},
			{6.8,3.0,5.5,2.1},
			{5.7,2.5,5.0,2.0},
			{5.8,2.8,5.1,2.4},
			{6.4,3.2,5.3,2.3},
			{6.5,3.0,5.5,1.8},
			{7.7,3.8,6.7,2.2},
			{7.7,2.6,6.9,2.3},
			{6.0,2.2,5.0,1.5},
			{6.9,3.2,5.7,2.3},
			{5.6,2.8,4.9,2.0},
			{7.7,2.8,6.7,2.0},
			{6.3,2.7,4.9,1.8},
			{6.7,3.3,5.7,2.1},
			{7.2,3.2,6.0,1.8},
			{6.2,2.8,4.8,1.8},
			{6.1,3.0,4.9,1.8},
			{6.4,2.8,5.6,2.1},
			{7.2,3.0,5.8,1.6},
			{7.4,2.8,6.1,1.9},
			{7.9,3.8,6.4,2.0},
			{6.4,2.8,5.6,2.2},
			{6.3,2.8,5.1,1.5},
			{6.1,2.6,5.6,1.4},
			{7.7,3.0,6.1,2.3},
			{6.3,3.4,5.6,2.4},
			{6.4,3.1,5.5,1.8},
			{6.0,3.0,4.8,1.8},
			{6.9,3.1,5.4,2.1},
			{6.7,3.1,5.6,2.4},
			{6.9,3.1,5.1,2.3},
			{5.8,2.7,5.1,1.9},
			{6.8,3.2,5.9,2.3},
			{6.7,3.3,5.7,2.5},
			{6.7,3.0,5.2,2.3},
			{6.3,2.5,5.0,1.9},
			{6.5,3.0,5.2,2.0},
			{6.2,3.4,5.4,2.3},
			{5.9,3.0,5.1,1.8},
		};
		DoubleMatrix2D points = new DenseDoubleMatrix2D(data);
		
		float data2[][] = new float[data.length][data[0].length];
		for(int i = 0; i < data.length; i++)
			for(int j = 0; j < data[i].length; j++)
				data2[i][j] = (float) data[i][j];
		
		DistanceMatrix dmat1 = new DistanceMatrix(points, new EuclideanDistance());
		//visualizer.projection.distance.DistanceMatrix dmat = DistanceMatrixFactory.getInstance(data2, new Euclidean());
		
		KMedoidClusteringAlgorithm pam = new KMedoidClusteringAlgorithm();
		pam.dm = new EuclideanDistance();
		pam.numClusters = 15;
		
		//Kmedoids kmedois = new Kmedoids(15);
		//kmedois.execute(dmat);
		//int[] controlPoints = kmedois.getMedoids();
		
		int[] controlPoints = pam.cluster(null, null, dmat1);
		controlPoints[controlPoints.length-1] = 104;
		for (int i = 0; i < controlPoints.length; i++) {
			System.out.print(controlPoints[i] + " | ");
		}
		System.out.println();
		
		MultidimensionalProjection mp = new MultidimensionalProjection();
		mp.numberOfNeighbors = 10;
		double[][] ret = mp.project(dmat1, controlPoints);
		
		for(int i  =0; i < ret.length; i++){
			for(int j = 0; j < ret[i].length; j++)
				System.out.print(ret[i][j] + " ");
			System.out.println();
		}
		
		
//		System.out.println("#################### LSP ###################");
//		ProjectionData pdata = new ProjectionData();
//		pdata.controlPoints = controlPoints;
//		pdata.setControlPointsChoice(null);
//		pdata.setNumberControlPoints(15);
//		pdata.setProjectorType(ProjectorType.NNP);
//		pdata.setFractionDelta(0.8f);
//		pdata.setNumberIterations(50);
//		pdata.setNumberNeighborsConnection(10);
//		
//		LSPProjection2D lsp = new LSPProjection2D();
//		float[][] projection = lsp.project(dmat, pdata);
//		for(int i  =0; i < projection.length; i++){
//			for(int j = 0; j < projection[i].length; j++)
//				System.out.print(projection[i][j] + " ");
//			System.out.println();
//		}
	}
}
