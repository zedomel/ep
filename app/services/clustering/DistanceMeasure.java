package services.clustering;

import java.io.Serializable;

import cern.colt.matrix.DoubleMatrix1D;

public interface DistanceMeasure extends Serializable{
	
	/**
	 * Calculates the distance between two instances.
	 * 
	 * @param i
	 *            the first instance
	 * @param j
	 *            the second instance
	 * @return the distance between the two instances
	 */
	public double measure(DoubleMatrix1D x, DoubleMatrix1D y);
	
	public double measure(org.carrot2.mahout.math.matrix.DoubleMatrix1D x, org.carrot2.mahout.math.matrix.DoubleMatrix1D y);

	/**
	 * Returns whether the first distance, similarity or correlation is better
	 * than the second distance, similarity or correlation.
	 * 
	 * Both values should be calculated using the same measure.
	 * 
	 * For similarity measures the higher the similarity the better the measure,
	 * for distance measures it is the lower the better and for correlation
	 * measure the absolute value must be higher.
	 * 
	 * @param x
	 *            the first distance, similarity or correlation
	 * @param y
	 *            the second distance, similarity or correlation
	 * @return true if the first distance is better than the second, false in
	 *         other cases.
	 */
	public boolean compare(double x, double y);

	/**
	 * Returns the value that this distance metric produces for the lowest
	 * distance or highest similarity. This is mainly useful to initialize
	 * variables to be used in comparisons with the compare method of this
	 * class.
	 * 
	 * 
	 * 
	 * @return minimum possible value of the distance metric
	 */
	public double getMinValue();

	/**
	 * Returns the value that this distance metric produces for the highest
	 * distance or lowest similarity. This is
	 * mainly useful to initialize variables to be used in comparisons with the
	 * compare method of this class.
	 * 
	 * @return maximum possible value of the distance metric
	 */
	public double getMaxValue();
}
