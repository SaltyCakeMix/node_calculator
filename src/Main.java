import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class Main extends JPanel implements Runnable, MouseListener, ComponentListener {
	private static final long serialVersionUID = 1L;
	static int windowWidth = 1000;
	static int windowHeight = 800;
	private final int FPS = 60;
	private boolean running = false;
	private Graphics2D g = null;
	private BufferedImage image;
	private Thread thread;
	private boolean resized = true;

	// Controls input
	private final HashSet<Integer> keysHeld = new HashSet<Integer>(); 
	private final HashSet<Integer> keysPressed = new HashSet<Integer>();
	private final HashSet<Character> charsPressed = new HashSet<Character>();
	private final HashSet<Integer> mouseHeld = new HashSet<Integer>();
	private final HashSet<Integer> mouseClicked = new HashSet<Integer>();
	private final HashSet<Integer> mouseReleased = new HashSet<Integer>();
	private Point mousePos = new Point(0, 0);
	private int dragging = -1;
	private int inputNode = -1;
	private String input = "";
	private StringBuilder inputChars = new StringBuilder();
	private int inputPointer = 0;
	private boolean selectAll = false;
	private boolean tabbed = false;

	// Font
	final public static int fontSize = 16;
	final public static Font font = new Font("Trebuchet MS", Font.PLAIN, fontSize);
	final FontMetrics fm = getFontMetrics(font);
	final static int margin = 5;
	
	// Objects
	private ArrayList<Node> nodes = new ArrayList<Node>(0);
	private Point offset = new Point(0, 0);
	private final int spacing = 250;
	private final int loose = 75;
	private final float dampener = 0.9f;
	private final int connectionThicc = 3;
	
	// Error handling
	private String errorMessage = "";
	private int errorDur = 0;
	final private int errorDurTotal = 300;
	
	public static void main(String[] args) {
		JFrame window = new JFrame("Node.calc");
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		Main main = new Main();
		
		window.setContentPane(main);
		window.setAlwaysOnTop(false);
		window.setMinimumSize(new Dimension(100, 100));
		window.setResizable(true);
		window.pack();
		window.setLocationRelativeTo(null);
		window.setVisible(true);
		
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
			public boolean dispatchKeyEvent(KeyEvent e) {
				if(e.getID() == KeyEvent.KEY_PRESSED) {
					char ch = e.getKeyChar();
					if(Main.font.canDisplay(ch) && ch != '	' && ch != '\n') main.addChar(ch);
					main.addKey(e.getKeyCode());
				} else if(e.getID() == KeyEvent.KEY_RELEASED) {
					main.removeKey(e.getKeyCode());
				};
	            return false;
	        }
		});
	}
	
	public Main() {
		Dimension d = new Dimension(windowWidth, windowHeight);
		setSize(d);
		setPreferredSize(d);
		setFocusable(true);
		requestFocus();   
	}

	public void addNotify() {
		super.addNotify();
		if(thread == null) {
			thread = new Thread(this);
			thread.start();
		};
		addMouseListener(this);
		addComponentListener(this);
	};

	public void run() {
		running = true;

		long startTime;
		long totalTime = 0;
		long takenTime = 0;
		int frameCount = 0;
		long totalProcessTime = 0;
		long targetTime = 1000000000 / FPS;
		long waitDiff = 0;

		while(running) {
			startTime = System.nanoTime();

			gameUpdate();
			gameRender();
			gameDraw();

			// Calculating how long system needs to wait for
        	long processTime = System.nanoTime() - startTime;
        	long waitTime = targetTime - processTime + waitDiff;

			try {
				Thread.sleep(waitTime / 1000000);
			} catch(Exception e) {};

			takenTime = System.nanoTime() - startTime;
        	waitDiff = (long) (waitDiff*0.75 + (targetTime - takenTime)*0.25);
        	
        	frameCount++;
        	totalTime += takenTime;
        	totalProcessTime += processTime;
        	if(totalTime >= 1000000000) {
        		System.out.print(frameCount + " ");
        		System.out.println(1 - totalProcessTime / 1000000000f);
        		frameCount = 0;
        		totalTime = 0;
        		totalProcessTime = 0;
        	}
		};
	};

	public void gameUpdate() {
		updateMouse();

		checkDelete();
		
		checkInput();
		receiveInput();
		handleErrors();
		
		createNewNodes();
		updateNodeDrag();
		
		createForces();
		applyForces();
		
		keysPressed.clear();
		charsPressed.clear();
		mouseClicked.clear();
		mouseReleased.clear();
	}

	private void applyForces() {
		for(Node node : nodes) {
			node.move(windowWidth, windowHeight, dampener);
		};
	}

	private void createForces() {
		for(int i = 0; i < nodes.size(); i++) {
			Node node1 = nodes.get(i);
			Point p1 = node1.getPos();
			for(int j = i + 1; j < nodes.size(); j++) {
				Node node2 = nodes.get(j);
				Point p2 = node2.getPos();
				
				Point d = Functions.pointSub(p1, p2);
				double distance = Math.max(Math.abs(d.x), Math.abs(d.y)); // Results in more square-like arrangements
				double angle = Functions.direction(d);
				if(distance < spacing - loose || (distance > spacing + loose && node1.isRelated(node2))) {
					double base = 1.0 - distance / spacing;
					double force = base * Math.abs(base);
					double dx = Math.cos(angle) * force;
					double dy = Math.sin(angle) * force;
					node1.addForce(dx, dy);
					node2.addForce(-dx, -dy);
				};
			};
		};
	}

	private void handleErrors() {
		if(errorDur > 0) {
			errorDur--;
		};
	};
	
	private void checkInput() {
		if(mouseClicked.contains(MouseEvent.BUTTON3)) {
			if(inputNode == -1) {
				for(int i = 0; i < nodes.size(); i++) {
					Node node = nodes.get(i);
					if(node.pointCollide(mousePos)) {
						startInput(i);
						return;
					};
				};
			} else inputNode = -1;
		} else if(mouseReleased.contains(MouseEvent.BUTTON1) && dragging != inputNode) {
			inputNode = -1;
		}
	}
	
	private void receiveInput() {
		if(inputNode != -1 && !keysPressed.isEmpty()) {
			if(keysHeld.contains(KeyEvent.VK_CONTROL) && keysPressed.contains(KeyEvent.VK_A)) {
				selectAll = true;
			} else {
				// Adding chars
				for(char ch : charsPressed) {
					delAllCheck();
					inputChars.insert(inputPointer, ch);
					inputPointer++;
				};
				
				// Moving pointer
				if(keysPressed.contains(KeyEvent.VK_LEFT)) {
					if(selectAll) {
						selectAll = false;
						inputPointer = 0;
					} else inputPointer = (int)Functions.clamp(inputPointer - 1, 0, inputChars.length());
				} else if(keysPressed.contains(KeyEvent.VK_RIGHT)) {
					if(selectAll) {
						selectAll = false;
						inputPointer = inputChars.length();
					} else inputPointer = (int)Functions.clamp(inputPointer + 1, 0, inputChars.length());
				};
				
				// Delete
				if(keysPressed.contains(KeyEvent.VK_BACK_SPACE) || keysPressed.contains(KeyEvent.VK_DELETE)) {
					delAllCheck();
					if(!inputChars.isEmpty() && inputPointer != 0) {
						inputChars.deleteCharAt(inputPointer - 1);
						inputPointer--;
					};
				};
				
				input = inputChars.toString();
			};
			
			// Read enter input
			if(keysPressed.contains(KeyEvent.VK_ENTER) || keysPressed.contains(KeyEvent.VK_TAB)) {
				try {nodes.get(inputNode).updateValue(input, nodes, tabbed, inputNode);}
				catch(Exception e) {error(e.getMessage() != null ? e.getMessage() : "null error");};
				
				if(keysPressed.contains(KeyEvent.VK_TAB)) {
					input = tabbed ? nodes.get(inputNode).getInput() : nodes.get(inputNode).getName();
					inputChars = new StringBuilder(input);
					inputPointer = inputChars.length();
					selectAll = false;
					tabbed = !tabbed;
				}
				else inputNode = -1;
			};
			
			// Read escape input
			if(keysPressed.contains(KeyEvent.VK_ESCAPE)) inputNode = -1;
		};
	}

	private void delAllCheck() {
		if(selectAll) {
			inputChars.setLength(0);
			inputPointer = 0;
			selectAll = false;
		}
	};

	private void updateNodeDrag() {
		if (mouseHeld.contains(MouseEvent.BUTTON1)) {
			// Starts dragging
			if(mouseClicked.contains(MouseEvent.BUTTON1) && dragging == -1) {
				for(int i = 0; i < nodes.size(); i++) {
					Node node = nodes.get(i);
					if(node.pointCollide(mousePos)) {
						dragging = i;
						offset = Functions.pointSub(node.getPos(), mousePos);
						break;
					};
				};
			};
			
			// Dragging physics
			if (dragging != -1) {
				Node node = nodes.get(dragging);
				node.drag(mousePos.x + offset.x, mousePos.y + offset.y);
			}
		} else {
			dragging = -1;
		};
	}

	private void startInput(int i) {
		inputNode = i;
		input = nodes.get(inputNode).getInput();
		inputChars = new StringBuilder(input);
		inputPointer = inputChars.length();
		selectAll = false;
		tabbed = false;
	};
	
	private void createNewNodes() {
		if(keysHeld.contains(KeyEvent.VK_CONTROL) && keysPressed.contains(KeyEvent.VK_N)) {
			nodes.add(new Node(mousePos.x, mousePos.y, "name"));
			startInput(nodes.size() - 1);
		};
	}

	private void checkDelete() {
		if(keysPressed.contains(KeyEvent.VK_D)) {
			for(int i = 0; i < nodes.size(); i++) {
				Node node = nodes.get(i);
				if(node.pointCollide(mousePos)) {
					node.clearParents();		// This clears all connections that the node previously had
					node.clearChildren(i);		// and readjusts all indices that got shifted due to this node being deleted
					nodes.remove(node);
					for(; i < nodes.size(); i++) nodes.get(i).updateChildrenName(String.valueOf(i + 1), String.valueOf(i));
					return;
				};
			};
		};
	};
	
	private void updateMouse() {
		mousePos = MouseInfo.getPointerInfo().getLocation();
		if(this.isShowing()) {
			mousePos = Functions.pointSub(mousePos, this.getLocationOnScreen());
		};
	};

	private void error(String s) {
		errorMessage = s;
		errorDur = errorDurTotal;
	};
	
	
	public void gameRender() {
		if (resized) {
			if(g != null) {
				g.dispose();
			};
			Dimension r = this.getSize();
			windowWidth = r.width;
			windowHeight = r.height;
			
			image = new BufferedImage(windowWidth, windowHeight,
					BufferedImage.TYPE_INT_RGB);
			g = image.createGraphics();
			g.setStroke(new BasicStroke(connectionThicc));
			g.setFont(font);
			resized = false;
		};
		
		// Draw background color
		g.setColor(new Color(0, 0, 0));
		g.fillRect(0, 0, windowWidth, windowHeight);
		
		// Draw connections
		g.setColor(new Color(125, 75, 125));
		for(int i = 0; i < nodes.size(); i++) {
			for(int j = i + 1; j < nodes.size(); j++) {
				Node node1 = nodes.get(i);
				Node node2 = nodes.get(j);
				if(node1.isRelated(node2)) {
					Point pos1 = node1.getCenter();
					Point pos2 = node2.getCenter();
					g.drawLine(pos1.x, pos1.y, pos1.x, pos2.y);
					g.drawLine(pos1.x, pos2.y, pos2.x, pos2.y);
				};
			};
		};
		
		// Draw nodes
		for(int i = 0; i < nodes.size(); i++) {
			nodes.get(i).draw(g, fm, i);
		};
		
		// Draw input text box
		if(inputNode != -1) {
			Node node = nodes.get(inputNode);
			node.drawInput(g, input, fm, inputPointer, selectAll, tabbed);
			
			if(!input.isEmpty()) {
				g.drawString(String.valueOf(inputChars.charAt((inputPointer >= 1 ? inputPointer : 1) - 1)), 0, 50);
			};
		};
		
		// Draw error message
		if(errorDur > 0) {
			int alpha = 255 * errorDur / errorDurTotal;
			g.setColor(new Color(255, 255, 255, alpha));
			g.drawString(errorMessage, margin, windowHeight - margin);
		};
	}
	

	public void gameDraw() {
		Graphics2D g2 = (Graphics2D) this.getGraphics();
		g2.drawImage(image, 0, 0, null);
		g2.dispose();
	};
	
	
	public void mousePressed(MouseEvent e) { //same thing for mouse buttons
		int key = e.getButton();

		mouseHeld.add(key);
		mouseClicked.add(key);
	}	

	public void mouseReleased(MouseEvent e) {
		int key = e.getButton();
		
		mouseHeld.remove(key);
		mouseReleased.add(key);
	}
	
	public void componentResized(ComponentEvent componentEvent) {
		resized = true;
	};

	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {}
	public void componentMoved(ComponentEvent e) {}
	public void componentShown(ComponentEvent e) {}
	public void componentHidden(ComponentEvent e) {}

	BufferedImage loadImage(String input) {
		try {
			return ImageIO.read(new File("images/" + input));
		} catch (IOException exc) {
			System.out.println("Error opening image file: " + exc.getMessage());
		};
		return new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB);
	}
	
	public void addKey(int i) {
		keysPressed.add(i);
		keysHeld.add(i);
	};
	
	public void removeKey(int i) {
		keysHeld.remove(i);
	};
	
	public void addChar(char ch) {
		charsPressed.add(ch);
	};
};