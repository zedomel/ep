package services.clustering;

import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;

public class DistanceMatrix extends DenseDoubleMatrix2D
implements Cloneable
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1782580878145135666L;
	
	protected int nrElements;
	protected double maxDistance;
	protected double minDistance;

	public DistanceMatrix(int nrElements) {
		super(nrElements, nrElements);
		this.maxDistance = Float.NEGATIVE_INFINITY;
		this.minDistance = Float.POSITIVE_INFINITY;
		this.nrElements = nrElements;
	}

	public DistanceMatrix(DoubleMatrix2D matrix, DistanceMeasure diss)
	{
		super(matrix.rows(), matrix.rows());
		
		this.nrElements = matrix.rows();
		this.maxDistance = Float.NEGATIVE_INFINITY;
		this.minDistance = Float.POSITIVE_INFINITY;

		for (int i = 0; i < this.nrElements; i++) {
			for (int j = i+1; j < this.nrElements; j++) {
				double distance = diss.measure(matrix.viewRow(i), matrix.viewRow(j));
				setDistance(i, j, distance);
				setDistance(j, i, distance);
			}
		}
	}

	public DistanceMatrix(org.carrot2.mahout.math.matrix.DoubleMatrix2D matrix, DistanceMeasure diss) {
		this(new DenseDoubleMatrix2D(matrix.toArray()), diss); 
	}

	public void setDistance(int indexA, int indexB, double value)
	{
		assert ((indexA >= 0) && (indexA < this.nrElements) && (indexB >= 0) && (indexB < this.nrElements)) : "ERROR: index out of bounds!";

		this.setQuick(indexB, indexA, value);

		if ((this.minDistance > value) && (value >= 0.0F)) {
			this.minDistance = value;
		}
		else if ((this.maxDistance < value) && (value >= 0.0F)) {
			this.maxDistance = value;
		}
	}

	public double getDistance(int indexA, int indexB)
	{
		assert ((indexA >= 0) && (indexA < this.nrElements) && (indexB >= 0) && (indexB < this.nrElements)) : "ERROR: index out of bounds!";
		return this.getQuick(indexA, indexB);
	}

	public double getMaxDistance()
	{
		return this.maxDistance;
	}

	public double getMinDistance()
	{
		return this.minDistance;
	}

	public int getElementCount()
	{
		return this.nrElements;
	}
}