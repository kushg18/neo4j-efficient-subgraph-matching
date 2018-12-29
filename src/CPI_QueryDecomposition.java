/**
 * CS729 Group Project:
 * CPI_QueryDecomposition.java
 * This program perform the query decomposition
 * separate the given query to core, forest and leaf structure
 * Author:
 * Gevaria, Harnisha (hgg5350@g.rit.edu)
 * Gevaria, Kushal (kgg5247@rit.edu)
 * Ku, Wei-Yao (wxk6489@rit.edu)
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class CPI_QueryDecomposition {

	// User give the path for query file pah
	static String filePath = "C:\\Harnisha\\Rochester Institute of Technology\\Courses\\Graph Database\\Assignments\\Proteins\\Proteins\\";

	// computing pattern graph and store them here
	ArrayList<String> queryNodes;
	HashMap<Integer, ArrayList<Integer>> queryGraph;
	HashMap<Integer, ArrayList<Integer>> originalQueryGraph;

	HashMap<Integer, ArrayList<Integer>> core;
	HashMap<Integer, ArrayList<Integer>> forest;
	HashMap<Integer, ArrayList<Integer>> leaf;

	int noOfQueryNodes;

	/**
	 * computes the query file and stores the pattern graph in the adjacency
	 * list format
	 * 
	 * @param file
	 */
	private void computePatternGraph(File file) {

		originalQueryGraph = new HashMap<>();
		queryGraph = new HashMap<>();
		core = new HashMap<>();
		forest = new HashMap<>();

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			String input[];

			line = br.readLine();
			int noOfNodes = Integer.parseInt(line);
			noOfQueryNodes = noOfNodes;
			// System.out.println("Loading " + noOfNodes);

			queryNodes = new ArrayList<String>();
			for (int i = 0; i < noOfNodes; i++) {
				input = br.readLine().split(" ");
				queryNodes.add(input[1]);
			}
			// System.out.println(patternNodes);

			int noOfEdges;
			ArrayList<Integer> neighbours;
			int j = 0;
			while ((line = br.readLine()) != null) {
				noOfEdges = Integer.parseInt(line);

				neighbours = new ArrayList<>();
				for (int i = 1; i <= noOfEdges; i++) {
					input = br.readLine().split(" ");
					neighbours.add(Integer.parseInt(input[1]));
				}
				queryGraph.put(j, neighbours);
				originalQueryGraph.put(j, neighbours);
				core.put(j, new ArrayList<Integer>(neighbours));
				forest.put(j, new ArrayList<Integer>(neighbours));

				j++;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * private void copyOfQueryGraph() { for (Entry<Integer, ArrayList<Integer>>
	 * entrySet : queryGraph.entrySet()) { core.put(entrySet.getKey(), new
	 * ArrayList<>(entrySet.getValue())); forest.put(entrySet.getKey(), new
	 * ArrayList<>(entrySet.getValue())); leaf.put(entrySet.getKey(), new
	 * ArrayList<>(entrySet.getValue())); } }
	 */

	/**
	 * Separate Query graph to core and forrest
	 */
	private void performCoreForestDecomposition() {

		ArrayList<Integer> oneDegreeNodes = new ArrayList<>();

		while (true) {
			//core as the structure without one degree node
			oneDegreeNodes = new ArrayList<>();
			for (int key : core.keySet()) {
				if (core.get(key).size() <= 1) {
					oneDegreeNodes.add(key);
				}
			}
			// System.out.println("one Degree Nodes: " + oneDegreeNodes);

			for (int removalNode : oneDegreeNodes) {
				core.remove(removalNode);
			}

			if (oneDegreeNodes.isEmpty()) {
				// System.out.println("Core Created:" + core);
				break;
			}
			for (ArrayList<Integer> value : core.values()) {
				value.removeAll(oneDegreeNodes);
			}
		}

		// creating the remaining forest as connect structure
		for (int key : core.keySet()) {
			forest.get(key).removeAll(core.get(key));

			// if no more elements left then that node is not part of the forest
			if (forest.get(key).isEmpty()) {
				forest.remove(key);
			}
		}
		// System.out.println("Temp Forest:" + forest);
	}

	/**
	 * Separate temporal forest to forrest and leaf collection
	 */
	private void performForestLeafDecomposition() {
		leaf = new HashMap<>();

		for (int key : forest.keySet()) {
			if (forest.get(key).size() == 1) {
				/*
				 * so if a key(node) or it's one child is part of the core then
				 * that node is considered as the part of forest and not the
				 * leaf
				 */
				if (core.containsKey(forest.get(key).get(0)) || core.containsKey(key)) {
					continue;
				}
				int parentKey = forest.get(key).get(0);
				if (forest.get(parentKey).size() <= 2) {
					leaf.put(key, new ArrayList<>(forest.get(key)));
					leaf.put(forest.get(key).get(0), new ArrayList<>(Arrays.asList(key)));
				}
			}
		}
		// System.out.println("Leaf: " + leaf);

		// creating the remaining forest
		for (int key : leaf.keySet()) {
			forest.get(key).removeAll(leaf.get(key));

			// if no more elements left then that node is not part of the forest
			if (forest.get(key).isEmpty()) {
				forest.remove(key);
			}
		}
		// System.out.println("Final Forest:" + forest);
	}

	/**
	 *  Separate Query graph to core, forrest and leaf collection
	 * @param queryFile
	 */
	void startQueryDecomposition(File queryFile) {
		// store query graph
		computePatternGraph(queryFile);
		// System.out.println(queryGraph);

		performCoreForestDecomposition();
		performForestLeafDecomposition();
	}

	public static void main(String[] args) {

		CPI_QueryDecomposition qd = new CPI_QueryDecomposition();

		// File queryFile = new File(filePath + "query//testQueryGraph.grf");
		// testQueryJustLeafandForest.grf
		// testQueryGraphWithEmptyForest.grf
		// for this the core is empty, what is leaf ?? and what is forest ask?
		// backbones_1EMA
		// ecoli_1RF8.8.sub.grf

		File queryFile = new File(filePath + "query//ecoli_1RF8.128.sub.grf");

		qd.startQueryDecomposition(queryFile);
	}

}
