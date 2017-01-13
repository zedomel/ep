package services.mp;

import services.clustering.DistanceMatrix;

public class NearestNeighborProjection
{
	
	private static final float EPSILON = 1.0E-5F;

	public double[][] project(DistanceMatrix dmat)
	{
		double[][] projection = new double[dmat.getElementCount()][];
		for (int i = 0; i < dmat.getElementCount(); i++) {
			projection[i] = new double[2];
		}
		
		projection[0][0] = 0.0F;
		projection[0][1] = 0.0F;
		projection[1][0] = 0.0F;
		projection[1][1] = dmat.getDistance(0, 1);

		for (int x = 2; x < dmat.getElementCount(); x++) {
			int q = 0;int r = 1;

			double minDistance1 = dmat.getMaxDistance();
			double minDistance2 = dmat.getMaxDistance();

			for (int ins = 0; ins < x; ins++) {
				double distance = dmat.getDistance(x, ins);

				if (minDistance1 > distance) {
					r = q;
					minDistance2 = minDistance1;
					q = ins;
					minDistance1 = distance;
				} else if (minDistance2 > distance) {
					minDistance2 = distance;
					r = ins;
				}
			}

			Circle circle1 = new Circle();
			Circle circle2 = new Circle();

			circle1.center.x = projection[q][0];
			circle1.center.y = projection[q][1];
			circle1.radius = dmat.getDistance(q, x);

			circle2.center.x = projection[r][0];
			circle2.center.y = projection[r][1];
			circle2.radius = dmat.getDistance(r, x);

			if (circle1.center.x - circle2.center.x < EPSILON) {
				circle1.center.x += EPSILON;
			}

			if (circle1.center.y - circle2.center.y < EPSILON) {
				circle1.center.y += EPSILON;
			}

			Pair resultingIntersections = new Pair();
			int number = intersect(circle1, circle2, resultingIntersections);

			if (number == 1) {
				projection[x][0] = resultingIntersections.first.x;
				projection[x][1] = resultingIntersections.first.y;
			}
			else
			{
				double distanceQX = dmat.getDistance(q, x);
				double distanceRX = dmat.getDistance(r, x);

				double distanceQX1 = Math.sqrt((projection[q][0] - resultingIntersections.first.x) * (projection[q][0] - resultingIntersections.first.x) + (projection[q][1] - resultingIntersections.first.y) * (projection[q][1] - resultingIntersections.first.y));
				double distanceQX2 = Math.sqrt((projection[q][0] - resultingIntersections.second.x) * (projection[q][0] - resultingIntersections.second.x) + (projection[q][1] - resultingIntersections.second.y) * (projection[q][1] - resultingIntersections.second.y));
				double distanceRX1 = Math.sqrt((projection[r][0] - resultingIntersections.first.x) * (projection[r][0] - resultingIntersections.first.x) + (projection[r][1] - resultingIntersections.first.y) * (projection[r][1] - resultingIntersections.first.y));
				double distanceRX2 = Math.sqrt((projection[r][0] - resultingIntersections.second.x) * (projection[r][0] - resultingIntersections.second.x) + (projection[r][1] - resultingIntersections.second.y) * (projection[r][1] - resultingIntersections.second.y));


				if (Math.abs(distanceQX / distanceQX1 - 1.0F) + Math.abs(distanceRX / distanceRX1 - 1.0F) < Math.abs(distanceQX / distanceQX2 - 1.0F) + Math.abs(distanceRX / distanceRX2 - 1.0F))
				{
					projection[x][0] = resultingIntersections.first.x;
					projection[x][1] = resultingIntersections.first.y;
				} else {
					projection[x][0] = resultingIntersections.second.x;
					projection[x][1] = resultingIntersections.second.y;
				}
			}
		}

		normalize2D(projection);

		return projection;
	}

	private int intersect(Circle circle1, Circle circle2, Pair intersect) {
		float lvdistance = (float)Math.sqrt((circle1.center.x - circle2.center.x) * (circle1.center.x - circle2.center.x) + (circle1.center.y - circle2.center.y) * (circle1.center.y - circle2.center.y));

		
		if (lvdistance > circle1.radius + circle2.radius) {
			double lvdifference = lvdistance - (circle1.radius + circle2.radius);

			if (circle1.center.x < circle2.center.x) {
				double m = (circle2.center.y - circle1.center.y) / (circle2.center.x - circle1.center.x);
				intersect.first.x = circle1.center.x;
				intersect.first.y = circle1.center.y;

				double lvdeloc = circle1.radius;
				lvdeloc += lvdifference / 2.0F; 
				double z = Math.sqrt(lvdeloc * lvdeloc / (1.0F + m * m));
				
				intersect.first.x = ((float)(intersect.first.x + z));
				intersect.first.y = ((float)(intersect.first.y + m * z));
			} else {
				double m = (circle1.center.y - circle2.center.y) / (circle1.center.x - circle2.center.x);
				intersect.first.x = circle2.center.x;
				intersect.first.y = circle2.center.y;

				double lvdeloc = circle2.radius;
				lvdeloc += lvdifference / 2.0F;
				double z = Math.sqrt(lvdeloc * lvdeloc / (1.0F + m * m));
				
				intersect.first.x = ((float)(intersect.first.x + z)); 
				intersect.first.y = ((float)(intersect.first.y + m * z));
			}

			return 1;
		}

		if (lvdistance < Math.abs(circle1.radius - circle2.radius))
		{
			if (circle1.radius > circle2.radius) {
				double lvdeloc = circle2.radius + lvdistance + (circle1.radius - circle2.radius - lvdistance) / 2.0F;
				double m = (circle2.center.y - circle1.center.y) / (circle2.center.x - circle1.center.x);
				double z = Math.sqrt(lvdeloc * lvdeloc / (1.0F + m * m));
				
				intersect.first.x = circle1.center.x;
				intersect.first.y = circle1.center.y;

				if ((circle2.center.y >= circle1.center.y) && (circle2.center.x >= circle1.center.x)) {
					intersect.first.x += z;
					intersect.first.y +=  Math.abs(m) * z;
					
				} else if ((circle2.center.y >= circle1.center.y) && (circle2.center.x <= circle1.center.x)) {
					intersect.first.x -= z;
					intersect.first.y += Math.abs(m) * z;
					
				} else if ((circle2.center.y <= circle1.center.y) && (circle2.center.x <= circle1.center.x)) {
					intersect.first.x -=  z;
					intersect.first.y -= Math.abs(m) * z;
					
				} else {
					intersect.first.x += z; 
					intersect.first.y -= Math.abs(m) * z;
				}
			} else {
				double lvdeloc = circle1.radius + lvdistance + (circle2.radius - circle1.radius - lvdistance) / 2.0F;
				double m = (circle1.center.y - circle2.center.y) / (circle1.center.x - circle2.center.x);
				double z = Math.sqrt(lvdeloc * lvdeloc / (1.0F + m * m));
				
				intersect.first.x = circle2.center.x;
				intersect.first.y = circle2.center.y;

				if ((circle1.center.y >= circle2.center.y) && (circle1.center.x >= circle2.center.x)) {
					intersect.first.x = ((float)(intersect.first.x + z)); 
					intersect.first.y = ((float)(intersect.first.y + Math.abs(m) * z));
					
				} else if ((circle1.center.y >= circle2.center.y) && (circle1.center.x <= circle2.center.x)) {
					intersect.first.x = ((float)(intersect.first.x - z)); 
					intersect.first.y = ((float)(intersect.first.y + Math.abs(m) * z));
				} else if ((circle1.center.y <= circle2.center.y) && (circle1.center.x <= circle2.center.x)) {
					intersect.first.x = ((float)(intersect.first.x - z));
					intersect.first.y = ((float)(intersect.first.y - Math.abs(m) * z));
				} else {
					intersect.first.x = ((float)(intersect.first.x + z)); 
					intersect.first.y = ((float)(intersect.first.y - Math.abs(m) * z));
				}
			}

			return 1;
		}

		double a = (circle1.radius * circle1.radius - circle2.radius * circle2.radius + lvdistance * lvdistance) / (2.0F * lvdistance);
		double h = Math.sqrt(circle1.radius * circle1.radius - a * a);
		double x2 = circle1.center.x + a * (circle2.center.x - circle1.center.x) / lvdistance;
		double y2 = circle1.center.y + a * (circle2.center.y - circle1.center.y) / lvdistance;
		double x31 = x2 + h * (circle2.center.y - circle1.center.y) / lvdistance;
		double x32 = x2 - h * (circle2.center.y - circle1.center.y) / lvdistance;
		double y31 = y2 - h * (circle2.center.x - circle1.center.x) / lvdistance;
		double y32 = y2 + h * (circle2.center.x - circle1.center.x) / lvdistance;

		intersect.first.x = x31;
		intersect.first.y = y31;

		intersect.second.x = x32;
		intersect.second.y = y32;

		return 2;
	}

	class Point {
		double x;
		double y;

		Point() {}
	}

	class Pair {
		NearestNeighborProjection.Point first = new NearestNeighborProjection.Point();
		NearestNeighborProjection.Point second = new NearestNeighborProjection.Point();

		Pair() {}
	}

	class Circle { Circle() {}

	public String toString() { return "center: (" + this.center.x + ", " + this.center.y + ") and radius: " + this.radius; }


	NearestNeighborProjection.Point center = new NearestNeighborProjection.Point();
	double radius;
	}

	public void normalize2D(double[][] projection)
	{
		double maxX = projection[0][0];
		double minX = projection[0][0];
		double maxY = projection[0][1];
		double minY = projection[0][1];

		for (int ins = 1; ins < projection.length; ins++) {
			if (minX > projection[ins][0]) {
				minX = projection[ins][0];
			}
			else if (maxX < projection[ins][0]) {
				maxX = projection[ins][0];
			}


			if (minY > projection[ins][1]) {
				minY = projection[ins][1];
			}
			else if (maxY < projection[ins][1]) {
				maxY = projection[ins][1];
			}
		}
		
		double endY = (maxY - minY) / (maxX - minX);
		
		for (int _ins = 0; _ins < projection.length; _ins++) {
			if (maxX - minX > 0.0D) {
				projection[_ins][0] = ((projection[_ins][0] - minX) / (maxX - minX));
			} else {
				projection[_ins][0] = 0.0F;
			}
			if (maxY - minY > 0.0D) {
				projection[_ins][1] = ((projection[_ins][1] - minY) / ((maxY - minY) * endY));
			} else {
				projection[_ins][1] = 0.0F;
			}
		}
	}

}