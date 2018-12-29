
/**
 * CS729 Group Project:
 * CPI_mainFile.java
 * main program for CPI, execute with CPI_Generation.java, CPI_QueryDecomposition.java
 * and neo4j database "ProtienForProject"
 * Author:
 * Gevaria, Harnisha (hgg5350@g.rit.edu)
 * Gevaria, Kushal (kgg5247@rit.edu)
 * Ku, Wei-Yao (wxk6489@rit.edu)
 */

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

public class CPI_mainFile {

	// User give the path for file
//	// for windows
	 static String dbPath = "C:\\Users\\HaRnIsHa\\Documents\\Neo4j\\ProtienForProject";
	 static String filePath = "C:\\Harnisha\\Rochester Institute of Technology\\Courses\\Graph Database\\Assignments\\Proteins\\Proteins\\";
	 static String queryPath = filePath + "query//";
	 static String targetPath = filePath + "target//";
	 static String groundTruthPath = filePath + "ground_truth//";

	// for MAC
//	static String dbPath = "./db/ProtienForProject";
//	static String filePath = "./Proteins/Proteins";
//	static String queryPath = filePath + "/query/";
//	static String targetPath = filePath + "/target/";
//	static String groundTruthPath = filePath + "/ground_truth/";

	// default target and query file for single file match
	static File queryFile;
	static File targetFile;

	static CPI_QueryDecomposition qd;
	static CPI_Generation cpi_generation;

	// Time Counter for experiment
	static float TotalDecomposedTime;
	static float TotalWholeTime;

	/**
	 * Preform DFS to separate connect (core,)forest and leaf component and
	 * following with CPI generation
	 * 
	 * @param component
	 */
	private void splitConnectedComponents(HashMap<Integer, ArrayList<Integer>> component) {

		if (component.size() == 0) {
			System.out.println("It is Empty! No CPI Generation");
			return;
		}

		HashMap<Integer, ArrayList<Integer>> currentVisitedComponent;

		HashMap<Integer, Boolean> visited = new HashMap<>();

		for (int key : component.keySet()) { // initiate visited collection
			visited.put(key, false);
		}

		// System.out.println("starting DFS");
		for (int key : component.keySet()) {
			// System.out.println("for key: " + key);
			if (!visited.get(key)) {
				currentVisitedComponent = new HashMap<>();

				int root = key;
				Stack<Integer> stack = new Stack<>();
				stack.push(root);

				while (!stack.isEmpty()) {
					int s = stack.pop();
					if (!visited.get(s)) {
						visited.replace(s, true);
						currentVisitedComponent.put(s, component.get(s));
					}
					for (int child : component.get(s)) {
						// System.out.println(visited);
						// System.out.println(child);
						if (!visited.get(child)) {
							stack.push(child);
						}
					}
				}
				// System.out.println("currentVissited: " +
				// currentVisitedComponent);
				// System.out.println("visited:" + visited);

				// call CPI construction
				qd.queryGraph = currentVisitedComponent;

				cpi_generation.startCPIGenration(qd, targetFile, queryFile, qd.queryGraph.size());

			}
		}

	}

	/**
	 * Send decomposed query for CPI generation
	 */
	private void callForDecomposed() {
		System.out.println("\n--------------Checking Decomposed Query Graph--------------");
		cpi_generation = new CPI_Generation();

		CPI_Generation.dbPath = dbPath;
		CPI_Generation.filePath = filePath;
		CPI_Generation.groundTruthPath = groundTruthPath;
		CPI_Generation.queryFile = queryFile;
		CPI_Generation.targetFile = targetFile;

		cpi_generation.connectNeo4j();

		System.out.println("\n--------------Checking Core--------------");
		splitConnectedComponents(qd.core);

		System.out.println("\n--------------Checking Forest--------------");
		splitConnectedComponents(qd.forest);

		System.out.println("\n--------------Checking Leaf--------------");
		splitConnectedComponents(qd.leaf);

		cpi_generation.closeNeo4jConnection();
	}

	/**
	 * Send whole query for CPI generation
	 */
	private void callForWholeQuery() {
		System.out.println("\n----------------Checking Whole Query Graph-----------------");
		cpi_generation = new CPI_Generation();

		CPI_Generation.dbPath = dbPath;
		CPI_Generation.filePath = filePath;
		CPI_Generation.groundTruthPath = groundTruthPath;
		CPI_Generation.queryFile = queryFile;
		CPI_Generation.targetFile = targetFile;

		cpi_generation.connectNeo4j();

		qd.queryGraph = qd.originalQueryGraph;
		cpi_generation.startCPIGenration(qd, targetFile, queryFile, qd.queryGraph.size());

		cpi_generation.closeNeo4jConnection();
	}

	/**
	 * perform single target and query match
	 */
	private void forSingleFile() {
		System.out.println("Target File: " + targetFile.getName());
		System.out.println("Query File: " + queryFile.getName());

		CPI_QueryDecomposition queryDecomposition = new CPI_QueryDecomposition();

		qd = queryDecomposition;

		// get the core, forest and leaf
		qd.startQueryDecomposition(queryFile);

		long startTime = System.currentTimeMillis();

		callForDecomposed();

		float elapsedTimeMinForD = (System.currentTimeMillis() - startTime) / (60 * 1000F);
		TotalDecomposedTime = TotalDecomposedTime + elapsedTimeMinForD; 
		System.out.println("Time for decomposed query graph: " + elapsedTimeMinForD + " (Min)");

		startTime = System.currentTimeMillis();

		callForWholeQuery();

		float elapsedTimeMinForW = (System.currentTimeMillis() - startTime) / (60 * 1000F);
		TotalWholeTime = TotalWholeTime + elapsedTimeMinForW; // exp unit can
																// remove later
		System.out.println("Time for whole query graph: " + elapsedTimeMinForW + " (Min)");

	}

	public static void main(String[] args) {
		// User setting: Choose execute fron single pair or a set of file
		// overwrite target/query file here
		queryFile = new File(queryPath + "ecoli_1RF8.8.sub.grf");
		targetFile = new File(targetPath + "mus_musculus_1U34.grf");
		
		// Single query
		CPI_mainFile runProject = new CPI_mainFile();
		runProject.forSingleFile();
		//

		/*
		// Batch query
		int queryGraphSize = 16; // set up query graph size for file protein.8,
									// 16, 32, 64, 128, 256
		int numTargetGraphSet = 1; // Desire number of target graph set matching

		File queryFolder = new File(queryPath);
		File[] listOfQueryFiles = queryFolder.listFiles();

		File targetFolder = new File(targetPath);
		File[] listOfTargetFiles = targetFolder.listFiles();

		long startTime = System.currentTimeMillis();
		int count = 0;
		//Iterator for target file
		//Note remove "//" part and only check one single target file

		int targetCount = 1; //
		System.out.println("listOfTargetFiles: " + listOfTargetFiles); //
		for (int targertIdx = 0; targertIdx < listOfTargetFiles.length; targertIdx = targertIdx//
				+ Math.round((listOfTargetFiles.length) / numTargetGraphSet)) { //

			System.out.println("targertIdx: " + targertIdx);
			targetFile = new File(targetPath + listOfTargetFiles[targertIdx]); //
			System.out.println("\n\n######## Target File: " + targetFile.getName() + " ########"); //
			System.out.println("Target File Set: " + targetCount); //

			for (File queryf : listOfQueryFiles) {
				if (queryf.toString().contains("." + queryGraphSize + ".")) {
					queryFile = queryf;
					System.out.println("\n-----------Query Set #" + (count + 1) + " ----------------------");
					CPI_mainFile runProject = new CPI_mainFile();
					runProject.forSingleFile();
					count++;
				}
			}
			targetCount++; //
		} // end for target //

		// Final preferment report for batch query
		System.out.println("\n###### Total Query Count: " + count + " ######");
		float elapsedTimeMin = (System.currentTimeMillis() - startTime) / (60 * 1000F);
		System.out.println(
				"Total Time for this " + count + " query graph (decomposed and whole): " + elapsedTimeMin + " (Min)");
		float avgTime = elapsedTimeMin / count;
		System.out.println("\nAverage Time for Decomposed Match: " + TotalDecomposedTime / count + " (Min)");
		System.out.println("Average Time for Whole Match: " + TotalWholeTime / count + " (Min) "
				+ "\nAverage Time per Query: " + avgTime + " (Min)");
	    */
	}

}
