import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;

public class Node {
	private String name = "";
	private double value = 0.0;
	private String input = "0.0";
	private double x = 0, y = 0, xVel = 0, yVel = 0;
	private final int size = 64;
	private final double r2A = Math.sqrt(Math.PI) / 2;
	
	private HashSet<Node> parents = new HashSet<Node>(0);
	private HashSet<Node> children = new HashSet<Node>(0);
	private final int recurseLimit = 100;
	
	// Front end
	final int margin = Main.margin;
	final int fontSize = Main.fontSize;
	
	public Node(double x, double y, String name) {
		this.x = x - size/2;
		this.y = y - size/2;
		this.name = name;
	};
	
	public void draw(Graphics2D g, FontMetrics fm, int i) {
		g.setColor(new Color(150, 100, 150));
		g.fillOval((int)x, (int)y, size, size);
		
		g.setColor(new Color(255, 255, 255));
		String stringValue = Double.isNaN(value) ? "N/A" : Double.toString(value);
		int sWidth = fm.stringWidth(stringValue);
		String nameValue = "[" + String.valueOf(i) + "] " + name;
		int nWidth = fm.stringWidth(nameValue);
		g.drawString(stringValue, (int)x + (size - sWidth)/2, (int)y + (size + fontSize)/2);
		g.drawString(nameValue, (int)x + (size - nWidth)/2, (int)y + size + fontSize);
	};
	
	public void drag(int x, int y) {
		this.x = x;
		this.y = y;
		xVel = yVel = 0;
	};
	
	public Point getPos() {
		return new Point((int)x, (int)y);
	};
	
	public Point getCenter() {
		return new Point((int)x + size/2, (int)y + size/2);
	};
		
	
	public void addForce(double dx, double dy) {
		xVel += dx;
		yVel += dy;
	};
	
	public void move(int width, int height, float dampener) {
		x = Functions.clamp(x + xVel, 0, width - size);
		y = Functions.clamp(y + yVel, 0, height - size);
		xVel *= dampener;
		yVel *= dampener;
	};
	
	public boolean pointCollide(Point p) {
		return Functions.pointRectCollide(p.x, p.y, x + size/2*(1-r2A), y, x + size/2*(1+r2A), y + size);
	};
	
	public void drawInput(Graphics2D g, String s, FontMetrics fm, int pointer, boolean select, boolean tabbed) {
		int sWidth = fm.stringWidth(s);
		
		int x1 = (int)x + (size - sWidth)/2;
		int y1 = (int)y + (tabbed ? size : (size - fontSize)/2);
		
		g.setColor(new Color(255, 255, 255));
		g.fillRect(x1 - margin, y1 - margin, sWidth + margin*2, fontSize + margin*2);
		
		g.setColor(new Color(0, 0, 0));
		g.drawString(s, x1, y1 + fontSize);
		
		x1 += fm.stringWidth(s.substring(0, pointer));
		g.drawLine(x1, y1, x1, y1 + fontSize);
	};
	
	public void updateValue(String s, ArrayList<Node> nodes, boolean tabbed, int i) {
		if(tabbed) {
			if(!name.equals(s)) {
				if(s.matches("^[\sa-zA-Z]*$")) {
					updateChildrenName(name, s);
					name = s;
				} else throw new RuntimeException("Node name cannot contain numbers or symbols");
			};
		} else if(!input.equals(s)) {
			double tempValue = value;
			HashSet<Node> tempParents = new HashSet<Node>(parents);
			clearParents();
			try {
				String noSpace = s.replaceAll(" ", "");
				if(noSpace.isEmpty()) {
					value = 0;
					input = "0.0";
				} else {
					input = s;
					if(noSpace.charAt(0) == '=') {
						value = eval(noSpace.substring(1), nodes);
					} else {
						try {
							value = Double.valueOf(noSpace);
							input = String.valueOf(value);
						} catch(Exception e) {throw new RuntimeException(s + "cannot be parsed as a number");};
					};
				};
			} catch(Exception e) {
				parents = tempParents;
				for(Node node : parents) node.addChild(this);
				
				value = Double.NaN;
				updateChildrenValue(nodes, 0);
				throw e;
			};
			
			if(tempValue != value) updateChildrenValue(nodes, 0);
		};
	};
	
	private double eval(final String str, ArrayList<Node> nodes) {
		Node host = this;
	    return new Object() {
	        int pos = -1, ch;
	        
	        void nextChar() {
	            ch = (++pos < str.length()) ? str.charAt(pos) : -1;
	        }
	        
	        boolean eat(int charToEat) {
	            if (ch == charToEat) {
	                nextChar();
	                return true;
	            }
	            return false;
	        }
	        
	        double parse() {
	            nextChar();
	            double x = parseExpression();
	            if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char)ch);
	            return x;
	        }
	        
	        double parseExpression() {
	            double x = parseTerm();
	            while(true) {
	                if      (eat('+')) x += parseTerm(); // addition
	                else if (eat('-')) x -= parseTerm(); // subtraction
	                else return x;
	            }
	        }
	        
	        double parseTerm() {
	            double x = parseFactor();
	            while(true) {
	                if      (eat('*')) x *= parseFactor(); // multiplication
	                else if (eat('/')) x /= parseFactor(); // division
	                else return x;
	            }
	        }
	        
	        double parseFactor() {
	            if (eat('+')) return +parseFactor(); // unary plus
	            if (eat('-')) return -parseFactor(); // unary minus
	            
	            double x = value;
	            int startPos = pos;
	            if (eat('(')) { // parentheses
	                x = parseExpression();
	                if (!eat(')')) throw new RuntimeException("Missing ')'");
	            } else if (Character.isDigit(ch) || ch == '.') { // numbers
	                while (Character.isDigit(ch) || ch == '.') nextChar();
	                x = Double.parseDouble(str.substring(startPos, pos));
	            } else if (Character.isLetter(ch)) { // functions
	                while (Character.isLetter(ch)) nextChar();
	                String func = str.substring(startPos, pos);
	                
	                if (eat('(')) {
	                    x = parseExpression();
	                    if (!eat(')')) throw new RuntimeException("Missing ')' after argument of " + func);
	                } else x = parseFactor();
	                
	                if (func.equals("sqrt")) x = Math.sqrt(x);
	                else if (func.equals("sin")) x = Math.sin(Math.toRadians(x));
	                else if (func.equals("cos")) x = Math.cos(Math.toRadians(x));
	                else if (func.equals("tan")) x = Math.tan(Math.toRadians(x));
	                else throw new RuntimeException("Unknown function: " + func);
	            } else if (eat('@')) { // node referencing
	            	if(Character.isLetter((char) ch)) {
		            	while (Character.isLetter((char) ch)) nextChar();
		            	boolean unchanged = true;
		            	String nodeName = str.substring(startPos + 1, pos);
		            	
		            	if(nodeName.isBlank()) throw new RuntimeException("Invalid reference to node");
		            	
		            	for(Node node : nodes) {
		            		if(node != host && node.name.replaceAll(" ", "").equals(nodeName)) {
		            			x = node.value;
		            			parents.add(node);
			            		node.addChild(host);
		            			unchanged = false;
		            			break;
		            		};
		            	};
		            	
		            	if(unchanged) throw new RuntimeException("Node " + nodeName + " does not exist");
	            	} else if(Character.isDigit((char) ch)) {
	            		while (Character.isDigit((char) ch)) nextChar();
		            	String nodeName = str.substring(startPos + 1, pos);
		            	int nodeIndex = Integer.valueOf(nodeName);
		            	
		            	try {
		            		Node node = nodes.get(nodeIndex);
		            		x = node.value;
		            		parents.add(node);
		            		node.addChild(host);
	            		} catch(Exception e) {throw new RuntimeException("Node " + nodeName + " does not exist");};
	            	} else throw new RuntimeException("Invalid reference to node");
	        	} else throw new RuntimeException("Unexpected character: " + (char)ch);
	            
	            if (eat('^')) x = Math.pow(x, parseFactor()); // exponentiation
	            
	            return x;
	        }
	    }.parse();
	}

	public String getInput() {
		return input;
	};
	
	public String getName() {
		return name;
	};
	
	public void addChild(Node n) {
		children.add(n);
	};
	
	public void removeChild(Node n) {
		children.remove(n);
	};
	
	private void updateChildrenValue(ArrayList<Node> nodes, int recurse) {
		if(recurse > recurseLimit) {
			value = Double.NaN;
			throw new RuntimeException("Cyclical referencing error");
		};
		try {
			for(Node node : children) node.updateValue(nodes, recurse);
		} catch(Exception e) {
			value = Double.NaN;
			throw e;
		};
	};
	
	private void updateValue(ArrayList<Node> nodes, int recurse) {
		double tempValue = value;
		value = eval(input.replaceAll(" ", "").substring(1), nodes);
		if(tempValue != value) updateChildrenValue(nodes, recurse + 1);
	};

	public void updateChildrenName(String oldName, String newName) {
		for(Node node : children) node.updateName(oldName, newName);
	};
	
	private void updateName(String oldName, String newName) {
		String noSpace = newName.replaceAll(" ", "");
		input = input.replaceAll(oldName, noSpace);
	};
	
	public void clearParents() {
		for(Node node : parents) node.removeChild(this);
		parents.clear();
	};
	
	public void clearChildren(int i) {
		String index = "@" + String.valueOf(i);
		for(Node node : children) node.unlink(name, index);
	};
	
	private void unlink(String parentName, String index) {
		value = Double.NaN;
		input = input.replaceAll(parentName, "NA").replaceAll(index, "@NA");
	};
	
	public boolean isRelated(Node n) {
		return children.contains(n) || parents.contains(n);
	};
}