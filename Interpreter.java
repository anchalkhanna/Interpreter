package interpreter;

import gui.RobotController;

import gui.RobotGui;

import java.util.HashMap;
import java.util.Stack;

import parser.Parser;
import tokenizer.Token;
import tree.Tree;
import boardGame.Board;

/**
 * Class Interpreter
 * 
 * @author Anchal Khanna
 * @version April 11,2012
 * 
 */

public class Interpreter extends Thread {

	HashMap<String, Tree<Token>> procedures = new HashMap<String, Tree<Token>>();
	Stack<HashMap<String, Integer>> activationRecordStack = new Stack<HashMap<String, Integer>>();

	RobotController robotController;
	Tree<Token> ast;
	Board board;
	boolean paused = false;
	boolean stopped = false;

	private Runnable programCompletedRunnable;

	/**
	 * The class throws an exception if the program is stopped
	 * 
	 * 
	 */
	class ProgramStoppedException extends Exception {

		private static final long serialVersionUID = 1L;

		public String toString() {
			return "Exception thrown because program stopped";
		}
	}

	/**
	 * Constructor for the interpreter
	 * 
	 * @param board
	 *            - the of the GUI
	 * @param programCompletedRunnable
	 *            to see if the program run s complete
	 * @param ast
	 *            the abstract syntax tree which we want to interpret
	 */
	public Interpreter(Board board, Tree<Token> program,
			Runnable programCompletedRunnable) {

		robotController = new RobotController(board);

		this.ast = program;
		this.board = board;
		this.programCompletedRunnable = programCompletedRunnable;

	}

	/**
	 * Evaluates the given expression
	 * 
	 * @param expression
	 *            -this a Tree
	 * @return integer value of the expression
	 */
	int evaluateExpression(Tree<Token> expression) {

		String operand;

		int result = 0;

		// checking if the expression is a leaf node
		if (expression.isLeaf()) {
			return evaluateFactor(expression);
		}

		int resultOfFirstChild = evaluateExpression(expression.firstChild());
		operand = expression.getValue().text;

		if (expression.lastChild() == null) {
			if (operand.equals("+")) {
				result = resultOfFirstChild;
			}
			if (operand.endsWith("-")) {
				result = -resultOfFirstChild;
			}
		} else {
			int resultOfLastChild = evaluateExpression(expression.lastChild());

			if (operand.equals("+")) { //$NON-NLS-1$
				result = resultOfFirstChild + resultOfLastChild;
			}

			if (operand.equals("*")) { //$NON-NLS-1$
				return resultOfFirstChild * resultOfLastChild;
			}

			if (operand.equals("/")) {
				return resultOfFirstChild / resultOfLastChild;

			}
			if (operand.equals("%")) {
				return resultOfFirstChild % resultOfLastChild;
			}
			if (operand.endsWith("-")) {
				result = resultOfFirstChild - resultOfLastChild;
			}
		}

		return result;

	}

	/**
	 * Evaluates a given factor
	 * 
	 * @param factor
	 *            - it's type is Tree
	 * @return the corresponding value of the factor
	 * 
	 */
	int evaluateFactor(Tree<Token> factor) {

		/*
		 * <factor> ::= <variable> | <integer> | "row" | "column" | "distance"
		 */
		String factorValue = factor.getValue().text;

		if (factorValue.equals("row")) {
			return robotController.robotPiece.getRow();
		}

		else if (factorValue.equals("column")) {
			return robotController.robotPiece.getColumn();
		}

		else if (factorValue.equals("distance")) {
			return robotController.distance();
		} else {
			for (int i = activationRecordStack.size() - 1; i >= 0; i--) {
				HashMap<String, Integer> symbolTable = activationRecordStack
						.get(i);
				if (symbolTable.get(factorValue) != null) {
					return symbolTable.get(factorValue);
				}
			}
		}

		return Integer.parseInt(factorValue);
	}

	/**
	 * Evaluates a given condition
	 * 
	 * @param condition
	 * @return true when the condition is true
	 */

	boolean evaluateCondition(Tree<Token> condition) {
		/*
		 * <condition> ::= <expression> <comparator> <expression> | "seeing"
		 * <thing> | "holding" <thing> | "not" <condition>
		 */
		int valueOfExpression1, valueOfExpression2;
		boolean result;

		if ("seeing".equals(condition.getValue().text)) {
			String thing = condition.firstChild().getValue().text;

			result = robotController.seeingObject(thing);
			if (result == true) {
				return true;
			}
		}

		else if ("holding".equals(condition.getValue().text)) {
			String thing = condition.firstChild().getValue().text;
			result = robotController.holdingObject(thing);
			if (result == true) {
				return true;
			}
		}
		// checking for comparator
		else if ("<".equals(condition.getValue().text)) {
			valueOfExpression1 = evaluateExpression(condition.firstChild());
			valueOfExpression2 = evaluateExpression(condition.lastChild());
			if (valueOfExpression1 < valueOfExpression2)
				return true;
		} else if (">".equals(condition.getValue().text)) {
			valueOfExpression1 = evaluateExpression(condition.firstChild());
			valueOfExpression2 = evaluateExpression(condition.lastChild());
			if (valueOfExpression1 > valueOfExpression2)
				return true;
		} else if ("<=".equals(condition.getValue().text)) {
			valueOfExpression1 = evaluateExpression(condition.firstChild());
			valueOfExpression2 = evaluateExpression(condition.lastChild());
			if (valueOfExpression1 <= valueOfExpression2)
				return true;
		} else if (">=".equals(condition.getValue().text)) {
			valueOfExpression1 = evaluateExpression(condition.firstChild());
			valueOfExpression2 = evaluateExpression(condition.lastChild());
			if (valueOfExpression1 >= valueOfExpression2)
				return true;
		} else if ("==".equals(condition.getValue().text)) {
			valueOfExpression1 = evaluateExpression(condition.firstChild());
			valueOfExpression2 = evaluateExpression(condition.lastChild());
			if (valueOfExpression1 == valueOfExpression2)
				return true;
		} else if ("!=".equals(condition.getValue().text)) {
			valueOfExpression1 = evaluateExpression(condition.firstChild());
			valueOfExpression2 = evaluateExpression(condition.lastChild());
			if (valueOfExpression1 != valueOfExpression2)
				return true;
		}

		else if ("not".equals(condition.getValue().text)) {
			return (!evaluateCondition(condition.firstChild()));

		}

		return false;
	}

	/**
	 * Interprets a given command
	 * 
	 * @param command
	 * @throws ProgramStoppedException
	 * @throws InterruptedException
	 */
	void interpret(Tree<Token> command) throws InterruptedException,
			ProgramStoppedException {

		// <command> ::= <thought> | <action>

		int valueOfExpression;
		boolean resultOfCondition = false;
		int i = 0;

		while (paused == true) {
			sleep(100);
		}
		if (stopped == true) {
			throw new ProgramStoppedException();
		}

		// pauses the program for 100ms so that we can see the robot movement on
		// the board
		sleep(100);
		/*
		 * <thought> ::= "set" <variable> <expression> ";" | "repeat"
		 * <expression> <block> | "while" <condition> <block> | "if" <condition>
		 * <block> [ "else" <block> ] | "call" <name> { <expression> } ";"
		 */
		if ("set".equals(command.getValue().text)) {

			// Evaluates the value of the expression
			valueOfExpression = evaluateExpression(command.lastChild());
			String variableName = command.firstChild().getValue().text;

			for (i = activationRecordStack.size() - 1; i >= 0; i--) {
				HashMap<String, Integer> symbolTable = activationRecordStack
						.get(i);
				if (symbolTable.get(variableName) != null) {
					symbolTable.put(variableName, valueOfExpression);
					break;
				}
			}
			if (i == -1) {
				HashMap<String, Integer> symbolTable = activationRecordStack
						.peek();
				symbolTable.put(variableName, valueOfExpression);
			}
			// Puts the value in the symbol table

		} else if ("repeat".equals(command.getValue().text)) {
			valueOfExpression = evaluateExpression(command.firstChild());
			while (i < valueOfExpression) {
				interpret(command.lastChild());
				i++;
			}

		} else if ("while".equals(command.getValue().text)) {
			while (evaluateCondition(command.firstChild())) {
				interpret(command.lastChild());
			}

		} else if ("if".equals(command.getValue().text)) {
			Tree<Token> ifBlock = command.firstChild().nextSibling();
			Tree<Token> elseBlock = ifBlock.nextSibling();
			resultOfCondition = evaluateCondition(command.firstChild());
			if (resultOfCondition == true) {
				interpret(ifBlock);
			} else {
				if (elseBlock != null) {
					interpret(elseBlock);
				}
			}

		} else if ("call".equals(command.getValue().text)) {

			Tree<Token> procedureFromHashTable;
			Tree<Token> procedureName = command.firstChild();

			// Look up the procedure, by name, in your hash table of procedure
			// names
			procedureFromHashTable = procedures
					.get(procedureName.getValue().text);

			// create a new hash map
			HashMap<String, Integer> parameterValues = new HashMap<String, Integer>();

			Tree<Token> procedureHeader = procedureFromHashTable.firstChild();
			Tree<Token> block = procedureHeader.nextSibling();
			Tree<Token> procedureNameFromDefinition = procedureHeader
					.firstChild();

			Tree<Token> arguments = procedureNameFromDefinition.nextSibling();
			Tree<Token> parameters = procedureName.nextSibling();
			// Putting the argument values in the hash map
			while (arguments != null) {

				int expressionValue = (parameters != null) ? evaluateExpression(parameters)
						: 0;
				parameterValues.put(arguments.getValue().text, expressionValue);

				arguments = arguments.nextSibling();
				if (parameters != null)
					parameters = parameters.nextSibling();
			}
			// Adding the hash map on to the stack
			activationRecordStack.add(parameterValues);
			interpret(block);
			// pop the hash map from the stack after the function call has been
			// completed
			activationRecordStack.pop();
		}

		/*
		 * <action> ::= <move> <expression> ";"| "turn" <direction> ";"| "take"
		 * <thing> ";"| "drop" <thing> ";"| "stop" ";"
		 */
		else if ("forward".equals(command.getValue().text)) {
			valueOfExpression = evaluateExpression(command.firstChild());
			robotController.moveNumSquares(valueOfExpression, "forward");

		} else if ("back".equals(command.getValue().text)) {
			valueOfExpression = evaluateExpression(command.firstChild());
			robotController.moveNumSquares(valueOfExpression, "back");

		}

		else if ("turn".equals(command.getValue().text)) {

			if (command.firstChild().getValue().text.equals("right")) {
				robotController.turnRight();
			} else if (command.firstChild().getValue().text.equals("left")) {
				robotController.turnLeft();
			} else if (command.firstChild().getValue().text.equals("around")) {
				robotController.turnAround();
			}
		} else if ("take".equals(command.getValue().text)) {
			String thing = command.firstChild().getValue().text;

			robotController.pickup(thing);

		} else if ("drop".equals(command.getValue().text)) {
			String thing = command.firstChild().getValue().text;
			robotController.drop(thing);

		} else if ("stop".equals(command.getValue().text)) {
			stopProgram();

		} else if ("block".equals(command.getValue().text)) { //$NON-NLS-1$

			Tree<Token> instruction = command.firstChild();
			while (instruction != null) {
				interpret(instruction);
				instruction = instruction.nextSibling();
			}

		} else if ("def".equals(command.getValue().text)) {
			String nameofdef = command.firstChild().firstChild().getValue().text;
			// Adding the procedure on to the stack
			if (procedures.get(nameofdef) == null) {
				procedures.put(nameofdef, command);
			} else {
				stopProgram();
			}
			// else what should i do
		}
	}

	/**
	 * 
	 * Runs to interpret the program. This method isn't called directly; the GUI
	 * calls the Thread's start() method.
	 */

	public void run() {

		Tree<Token> block = ast.firstChild();
		Tree<Token> node = block;

		stopped = false;
		// clears the stack everytime a nw=ew program begins
		activationRecordStack.clear();
		try {
			while (node.hasNextSibling()) {
				node = node.nextSibling();
				interpret(node);
			}

			// create a new hash map and put it on the stack of hash maps
			HashMap<String, Integer> parameterValues = new HashMap<String, Integer>();
			activationRecordStack.add(parameterValues);
			interpret(block);
			activationRecordStack.pop();
		} catch (ProgramStoppedException | InterruptedException e) {
			// do nothing program is over
		}
		javax.swing.SwingUtilities.invokeLater(this.programCompletedRunnable);
	}

	/**
	 * @param b
	 *            - the boolean to set pause set the pause pause button true
	 *            should pause the interpreter, false should resume
	 *            interpretation
	 * 
	 */
	public void setPaused(boolean b) {
		paused = b;
	}

	/**
	 * To terminate interpretation of the current robot program
	 */
	public void stopProgram() {

		stopped = true;
	}

	/**
	 * Checks if the program is paused
	 * 
	 * @return the boolean value true if the program is paused
	 * 
	 */
	public boolean isPaused() {
		return paused;
	}
}