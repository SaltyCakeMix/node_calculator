import java.awt.Point;

class Functions {   
	public static double clamp(double val, double min, double max) {
		return Math.max(min, Math.min(max, val));
	}

	public static boolean rectRectCollide(double l1, double t1, double r1, double b1, double l2, double t2, double r2, double b2) {
		return !(t1 > b2 ||
			b1 <= t2 ||
			l1 > r2 || 
			r1 <= l2);
	};

	public static double distance(double x1, double y1, double x2, double y2) { //calculates euclidean distance using pythagorean theorom
		final double deltaX = Math.abs(x1 - x2);
		final double deltaY = Math.abs(y1 - y2);

		return Math.hypot(deltaX, deltaY);
	};
	
	public static double distance(Point p1, Point p2) {
		final double deltaX = Math.abs(p1.x - p2.x);
		final double deltaY = Math.abs(p1.y - p2.y);

		return Math.hypot(deltaX, deltaY);
	};
	
	public static Point pointSub(Point p1, Point p2) {
		return new Point(p1.x - p2.x, p1.y - p2.y);
	};
	
	public static double norm(Point p) {
		return Math.hypot(p.x, p.y); 
	};
	
	public static double direction(Point p) {
		return Math.atan2(p.y, p.x);
	};
	
	public static boolean pointRectCollide(double x, double y, double l, double t, double r, double b) {
		return (x > l &&
				x < r &&
				y > t &&
				y < b);
	};
};