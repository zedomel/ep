package services.mp;

public class Pair implements Comparable<Pair>
{
	public static final double EPSILON = 1.0E-5;

	public int index;

	public double value;

	public Pair(int index, double value)
	{
		this.index = index;
		this.value = value;
	}

	public int compareTo(Pair o)
	{
		if (this.value - ((Pair)o).value == EPSILON)
			return 0;
		if (this.value - ((Pair)o).value > EPSILON) {
			return 1;
		}
		return -1;

	}
}