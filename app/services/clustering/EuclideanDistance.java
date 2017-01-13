package services.clustering;

import cern.colt.matrix.DoubleMatrix1D;


public class EuclideanDistance implements DistanceMeasure{

	/**
	 * 
	 */
	private static final long serialVersionUID = 8035412745049926764L;

	@Override
	public double measure(DoubleMatrix1D x, DoubleMatrix1D y) {
        if (x.size() != y.size()) {
            throw new RuntimeException("Both instances should contain the same number of values.");
        }
        
        DoubleMatrix1D result = x.copy();
		result.assign(y, (xi, yi) -> {
			return (xi - yi)*(xi - yi);
		});
		double dist = result.zSum();
		return Math.sqrt(dist);
	}

	@Override
	public double measure(org.carrot2.mahout.math.matrix.DoubleMatrix1D x,
			org.carrot2.mahout.math.matrix.DoubleMatrix1D y) {
		
		org.carrot2.mahout.math.matrix.DoubleMatrix1D result = x.copy();
		result.assign(y, (xi, yi) -> {
			return (xi - yi)*(xi - yi);
		});
		double dist = result.zSum();
		return Math.sqrt(dist);
	}
	
	@Override
	public boolean compare(double x, double y) {
		return x < y;
	}

	@Override
	public double getMinValue() {
		return 0;
	}

	@Override
	public double getMaxValue() {
		return Double.POSITIVE_INFINITY;
	}

	
}
