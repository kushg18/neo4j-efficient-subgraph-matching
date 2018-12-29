
/**
 * CS729 Group Project:
 * CPI_Generation.java
 * This program compute the CPI map with
 * top Down CPI Construct and bottom Up CPI Refinement for giving query
 * and match final CPI with the ground turn result
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.commons.io.FilenameUtils;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

public class CPI_Generation {

	GraphDatabaseService db;
	CPI_QueryDecomposition qd;

	HashMap<Integer, ArrayList<Integer>> candidateSet;
	HashMap<Integer, Integer> candidateSize;

	int rootNode;
	int n;

	HashMap<Integer, ArrayList<Integer>> queryTree;
	HashMap<Integer, ArrayList<Integer>> levelNodes;
	HashMap<Integer, Integer> parents;

	HashMap<Integer, ArrayList<Integer>> C;
	HashMap<Integer, ArrayList<Integer>> UN;
	HashMap<Integer, Boolean> visited;

	// find neighbors of each v and store here
	// ArrayList<Long> neighbors;

	RelationshipType relation = RelationshipType.withName("con");
	Label CPILabel = Label.label("CPI_Structure");

	static String dbPath;
	static String filePath;
	static String groundTruthPath;

	static File queryFile;
	static File targetFile;

	/**
	 * Connects to the neo4j open data connection
	 */
	void connectNeo4j() {
		// System.out.println("Connecting to neo4j");
		db = new GraphDatabaseFactory().newEmbeddedDatabase(new File(dbPath));
	}

	/**
	 * Closes the connections to neo4j
	 */
	void closeNeo4jConnection() {
		db.shutdown();
	}

	/**
	 * delete older CPI in neo4j database
	 */
	private void deleteCPIStructure() {
		// System.out.println("Deleting the previous Structure......");
		try (Transaction tx = db.beginTx()) {
			ResourceIterator<Node> allNodes;
			allNodes = db.findNodes(CPILabel);

			while (allNodes.hasNext()) {
				Node node = allNodes.next();
				// System.out.println("Delete: " + node.getAllProperties());
				for (Relationship r : node.getRelationships()) {
					r.delete();
				}
				node.delete();
			}
			tx.success();
			tx.close();
		}
	}

	/**
	 * computes search space for the query graph in the given target graph
	 * 
	 * @param targetLabel
	 */
	private void computeCandidateSet(String targetLabel) {
		candidateSize = new HashMap<Integer, Integer>();
		ArrayList<Integer> sortedValues = new ArrayList<>();

		candidateSet = new HashMap<>();
		ArrayList<Integer> searchSpaceForaNode;
		ResourceIterator<Node> allNodes;

		for (int i : qd.queryGraph.keySet()) {
			searchSpaceForaNode = new ArrayList<>();
			int degreeOfU = qd.queryGraph.get(i).size();
			try (Transaction tx = db.beginTx()) {
				allNodes = db.findNodes(Label.label(targetLabel), "name", qd.queryNodes.get(i));
				Node node;
				while (allNodes.hasNext()) {
					node = allNodes.next();
					if (node.getDegree() >= degreeOfU) {
						searchSpaceForaNode.add((int) node.getId());
					}
				}
				candidateSet.put(i, searchSpaceForaNode);
			}
			// sorted values for root selection
			candidateSize.put(i, searchSpaceForaNode.size());
			sortedValues.add(searchSpaceForaNode.size());
		}
		// System.out.println("Candidate Size:" + candidateSize + "\n" +
		// candidateSet.size() );

		Collections.sort(sortedValues); // sort candidate for root selection
		// System.out.println(sortedValues);
		selectRoot();
	}

	/**
	 * Evaluate candidate node and pickup one as root
	 */
	// change code to consider only core nodes ?
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void selectRoot() {
		// Referred the code of sorting hash map based on value from
		// http://www.java2novice.com/java-interview-programs/sort-a-map-by-value/
		List list = new LinkedList(candidateSize.entrySet());

		Collections.sort(list, new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((Comparable) ((Map.Entry) (o1)).getValue()).compareTo(((Map.Entry) (o2)).getValue());
			}
		});

		HashMap sortedHashMap = new LinkedHashMap();
		for (Iterator it = list.iterator(); it.hasNext();) {
			Map.Entry entry = (Map.Entry) it.next();
			sortedHashMap.put(entry.getKey(), entry.getValue());
		}
		// Reference ends here

		// for top three doing CandVerify
		int j = 0;
		int root = -1;
		double candidateSize = 0;
		double queryNodeDegree = 0;
		double minValue = Double.POSITIVE_INFINITY;

		for (Object key : sortedHashMap.keySet()) {
			if (j == 3) {
				// found top three candidates for root node
				break;
			}
			int u = (int) key;
			// System.out.println("Top three nodes for root: " + u);

			ArrayList<Integer> removeNonCnd = new ArrayList<>();
			for (int v : candidateSet.get(u)) {
				if (!CandVerify(u, v)) {
					removeNonCnd.add(v);
				}
			}
			candidateSet.get(u).removeAll(removeNonCnd);
			j++;
			// System.out.println("new Candidate Set: " +
			// candidateSet.get(u).size());
			candidateSize = candidateSet.get(u).size();
			queryNodeDegree = qd.queryGraph.get(u).size();
			// System.out.println(candidateSize / queryNodeDegree);
			if (minValue > candidateSize / queryNodeDegree) {
				root = u;
				minValue = candidateSize / queryNodeDegree;
			}
		}
		System.out.println("Root Node:" + root);
		rootNode = root;
	}

	/**
	 * Evaluate candidate node in the target graph with the degree of neighbors
	 * 
	 * @param u
	 * @param v
	 * @return
	 */
	private boolean CandVerify(int u, int v) {

		int mndG = Integer.MIN_VALUE; // maximum of graph node neighbor degree
										// mnd
		int mndQ = Integer.MIN_VALUE; // maximum degree of query node neighbors

		int currQDeg = 0;
		for (int qNeigh : qd.queryGraph.get(u)) {
			currQDeg = qd.queryGraph.get(qNeigh).size();
			if (currQDeg > mndQ) {
				mndQ = currQDeg;
			}
		}

		// maximum degree of data node neighbors
		int currGDeg = 0;
		String currGLabel;
		HashMap<String, Integer> LnG = new HashMap<String, Integer>();

		try (Transaction tx = db.beginTx()) {
			Node currentV = db.getNodeById(Long.valueOf(v));
			Iterable<Relationship> allRelations = currentV.getRelationships();
			for (Relationship r : allRelations) {
				Node vNeigh = r.getOtherNode(currentV);
				currGDeg = vNeigh.getDegree();
				currGLabel = (String) vNeigh.getProperty("name");

				LnG.put(currGLabel, LnG.getOrDefault(currGLabel, 0) + 1);
				if (currGDeg > mndG) {
					mndG = currGDeg;
				}
			}
			tx.success();
		}

		// System.out.println("mndQ: " + mndQ);
		// System.out.println("mndG: " + mndG);

		// maximum neighbors number of candidate node should equal or great than
		// query node
		if (mndG < mndQ) {
			return false;
		}

		HashMap<String, Integer> LnQ = new HashMap<String, Integer>();
		// get unique labels of neighbors of u
		for (int qNeigh : qd.queryGraph.get(u)) {
			String currLabel = qd.queryNodes.get(qNeigh);
			if (LnQ.containsKey(currLabel)) {
				LnQ.put(currLabel, LnQ.get(currLabel) + 1);
			} else {
				LnQ.put(currLabel, 1);
			}
		}

		for (String label : LnQ.keySet()) {
			if (LnG.getOrDefault(label, 0) < LnQ.get(label)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Generate Query BFS tree for CPI mapping
	 */
	private void createQueryTree() {

		queryTree = new HashMap<>();
		levelNodes = new HashMap<>();
		parents = new HashMap<>();

		int level = 1;

		for (int i : qd.queryGraph.keySet()) {
			queryTree.put(i, new ArrayList<Integer>());
		}

		Queue<Integer> q1 = new LinkedList<>();
		Queue<Integer> q2 = new LinkedList<>();
		HashMap<Integer, Boolean> visited = new HashMap<Integer, Boolean>();

		q1.add(rootNode);

		levelNodes.put(level, new ArrayList<Integer>());
		levelNodes.get(level).add(rootNode);
		level++;
		visited.put(rootNode, true);

		while (!q1.isEmpty() || !q2.isEmpty()) {

			while (!q1.isEmpty()) {
				int node = q1.poll();
				for (Integer neighbour : qd.queryGraph.get(node)) {

					if (visited.getOrDefault(neighbour, false) == false) {
						q2.add(neighbour);
						parents.put(neighbour, node);
						queryTree.get(node).add(neighbour);
						queryTree.get(neighbour).add(node);
						visited.put(neighbour, true);

						if (levelNodes.containsKey(level)) {
							levelNodes.get(level).add(neighbour);
						} else {
							levelNodes.put(level, new ArrayList<Integer>());
							levelNodes.get(level).add(neighbour);
						}
					}
				}
			}
			level++;

			while (!q2.isEmpty()) {
				int node = q2.poll();
				for (Integer neighbour : qd.queryGraph.get(node)) {

					if (visited.getOrDefault(neighbour, false) == false) {
						q1.add(neighbour);
						parents.put(neighbour, node);
						queryTree.get(node).add(neighbour);
						queryTree.get(neighbour).add(node);
						visited.put(neighbour, true);

						if (levelNodes.containsKey(level)) {
							levelNodes.get(level).add(neighbour);
						} else {
							levelNodes.put(level, new ArrayList<Integer>());
							levelNodes.get(level).add(neighbour);
						}
					}
				}
			}
			level++;

		}

		// System.out.println(queryTree);
		// System.out.println(levelNodes);
		// System.out.println("Parents:" + parents);

	}

	/**
	 * perform top down evaluation to remove redundant node and store CPI map in
	 * the neo4j as Algorithm 3
	 * 
	 * @param targetLabel
	 */
	private void topDownCPI_Construct(String targetLabel) {

		C = new HashMap<>();
		visited = new HashMap<>();
		UN = new HashMap<>();

		for (int i : qd.queryGraph.keySet()) {
			// for (int i = 0; i < n; i++) {
			visited.put(i, false);
			UN.put(i, new ArrayList<Integer>());
			C.put(i, new ArrayList<Integer>());
		}

		ArrayList<Integer> rootCandidates = candidateSet.get(rootNode);
		// System.out.println(rootCandidates);
		C.put(rootNode, rootCandidates);

		visited.put(rootNode, true);

		int maxLevel = levelNodes.size();

		try (Transaction tx = db.beginTx()) {

			createNeo4jNodes(rootNode, targetLabel);

			for (int lev = 2; lev <= maxLevel; lev++) {
				System.out.println("At level: " + lev);
				// forward candidate generation
				// line 5-17
				for (int u : levelNodes.get(lev)) {
					int CNT = 0;
					// line 7-14
					for (int uDash : qd.queryGraph.get(u)) {
						if (!visited.get(uDash) && levelNodes.get(lev).contains(uDash)) {
							UN.get(u).add(uDash);
						} else if (visited.get(uDash)) {
							// line 11-14
							for (int vDash : C.get(uDash)) {
								String uLabel = qd.queryNodes.get(u);
								int uDegree = qd.queryGraph.get(u).size();

								// getting neighbours of vDash
								// ArrayList<Long> allV = new ArrayList<>();
								Node nodeVDash = db.getNodeById((long) vDash);
								Iterable<Relationship> allRelations = nodeVDash.getRelationships();
								for (Relationship r : allRelations) {
									Node v = r.getOtherNode(nodeVDash);
									// System.out.println("uLabel: " + uLabel +
									// " " + v.getProperty("name"));
									if (uLabel.equals(v.getProperty("name")) && v.getDegree() >= uDegree) {
										// System.out.println("inside line 11-14");
										int vCnt = (int) v.getProperty("cnt");
										if (vCnt == CNT) {
											v.setProperty("cnt", vCnt + 1);
										}
									}
								}
							}
							// line14
							CNT++;
						}
					}

					// line 15-16 inside for (int u : levelNodes.get(lev))
					ResourceIterator<Node> allNodes;

					allNodes = db.findNodes(Label.label(targetLabel));
					Node node;
					while (allNodes.hasNext()) {
						node = allNodes.next();
						// System.out.println(node.getAllProperties());
						if ((int) node.getProperty("cnt") == CNT) {
							if (CandVerify(u, (int) node.getId())) {
								C.get(u).add((int) node.getId());
							}
						}
						node.setProperty("cnt", 0);
					}

					// line 17
					visited.put(u, true);

				}

				// line 18-23
				for (int uIndex = levelNodes.get(lev).size() - 1; uIndex >= 0; uIndex--) {
					// System.out.println("In Reverse Order");
					int u = levelNodes.get(lev).get(uIndex);
					int CNT = 0;
					for (int uDash : UN.get(u)) {
						// line 11-14
						for (int vDash : C.get(uDash)) {
							String uLabel = qd.queryNodes.get(u);
							int uDegree = qd.queryGraph.get(u).size();

							// getting neighbours of vDash
							// ArrayList<Long> allV = new ArrayList<>();
							Node nodeVDash = db.getNodeById((long) vDash);
							Iterable<Relationship> allRelations = nodeVDash.getRelationships();
							for (Relationship r : allRelations) {
								Node v = r.getOtherNode(nodeVDash);
								// System.out.println("uLabel: " + uLabel +
								// " " + v.getProperty("name"));
								if (uLabel.equals(v.getProperty("name")) && v.getDegree() >= uDegree) {
									// System.out.println("inside line
									// 11-14");
									int vCnt = (int) v.getProperty("cnt");
									if (vCnt == CNT) {
										v.setProperty("cnt", vCnt + 1);
									}
								}
							}
						}
						// line14
						CNT++;
					}

					// line 21-22
					ArrayList<Integer> removeV = new ArrayList<>();
					for (int v : C.get(u)) {
						Node nodeV = db.getNodeById((long) v);
						int vcnt = (int) nodeV.getProperty("cnt");
						if (vcnt != CNT) {
							removeV.add(v);
						}
					}
					C.get(u).removeAll(removeV);

					// line 23 reset vcnt
					ResourceIterator<Node> allNodes;
					allNodes = db.findNodes(Label.label(targetLabel));
					Node node;
					while (allNodes.hasNext()) {
						node = allNodes.next();
						node.setProperty("cnt", 0);
					}
				}

				// create nodes
				for (int u : levelNodes.get(lev)) {
					createNeo4jNodes(u, targetLabel);
				}

				// line 24-28 Adjacency List Construction
				for (int u : levelNodes.get(lev)) {

					System.out.println("Creating Relations at level:" + lev);

					int up = parents.get(u);
					for (int vp : C.get(up)) {
						String uLabel = qd.queryNodes.get(u);

						Node nodeVp = db.getNodeById((long) vp);
						Iterable<Relationship> allRelations = nodeVp.getRelationships();

						for (Relationship r : allRelations) {

							Node v = r.getOtherNode(nodeVp);
							if (uLabel.equals(v.getProperty("name"))) {
								// System.out.println( "inside line // 11-14");
								if (C.get(u).contains((int) v.getId())) {
									// create edge in neo4j
									Node CPI_vp = db.findNode(CPILabel, "nodeId", vp);
									Node CPI_v = db.findNode(CPILabel, "nodeId", (int) v.getId());
									// need to remove multiple relations links
									boolean relationPresent = false;
									for (Relationship cpi_relation : CPI_vp.getRelationships()) {

										Node other_node = cpi_relation.getOtherNode(CPI_vp);
										if (other_node.getId() == CPI_v.getId()) {
											// System.out.println("Relationship present");
											relationPresent = true;
											break;
										}
									}

									if (!relationPresent) {
										// System.out.println("Not
										// present-----");
										CPI_vp.createRelationshipTo(CPI_v, relation);
									}
								}
							}
						}
					}
				}
			}
			tx.success();
			tx.close();
		} // end of transaction
			// System.out.println("C: "+ C);
			// System.out.println("UN: "+ UN);
			// System.out.println("Visited: " + visited);
	}

	/**
	 * Create CPI map in neo4j database for the all candidate for u label
	 * 
	 * @param u
	 * @param targetLabel
	 */
	private void createNeo4jNodes(int u, String targetLabel) {
		System.out.println("Creating Nodes for Label : " + u);

		// Label targetL = Label.label(targetLabel);
		// Label queryL = Label.label(queryLabel);

		ResourceIterator<Node> allNodes;
		Node node;

		for (int v : C.get(u)) {
			// if v already there then add extra label u
			// else create new v node in neo4j
			allNodes = db.findNodes(CPILabel);
			if (!allNodes.hasNext()) {
				Node newNode = db.createNode(CPILabel, Label.label(Integer.toString(u)));
				newNode.setProperty("nodeId", v);
				newNode.setProperty("name", qd.queryNodes.get(u));
				newNode.setProperty("originalId", db.getNodeById((long) v).getProperty("id"));
			} else {
				node = db.findNode(CPILabel, "nodeId", v);
				if (node == null) {
					Node newNode = db.createNode(CPILabel, Label.label(Integer.toString(u)));
					newNode.setProperty("nodeId", v);
					newNode.setProperty("name", qd.queryNodes.get(u));
					newNode.setProperty("originalId", db.getNodeById((long) v).getProperty("id"));
				} else {
					node.addLabel(Label.label(Integer.toString(u)));
				}
			}
		} // end fore each candidate v
	}

	/**
	 * perform bottom up evaluation to remove redundant node and update CPI map
	 * in the neo4j as Algorithm 4
	 * 
	 * @param targetLabel
	 */
	private void bottomUpCPI_Refinement(String targetLabel) {
		// line 1- 11
		int maxLevel = levelNodes.size();
		System.out.println("Performining Bottom up Refinement...\n");
		try (Transaction tx = db.beginTx()) {

			for (int lev = maxLevel - 1; lev > 0; lev--) {
				for (int u : levelNodes.get(lev)) {
					int CNT = 0;

					ArrayList<Integer> ULLNeigh = new ArrayList<>(qd.queryGraph.get(u));
					ULLNeigh.retainAll(levelNodes.get(lev + 1));
					// System.out.println("For u:" + u + " " + ULLNeigh);

					// line 3-4
					for (int uDash : ULLNeigh) {
						// line 11-14
						for (int vDash : C.get(uDash)) {
							String uLabel = qd.queryNodes.get(u);
							int uDegree = qd.queryGraph.get(u).size();

							Node nodeVDash = db.getNodeById((long) vDash);
							Iterable<Relationship> allRelations = nodeVDash.getRelationships();
							for (Relationship r : allRelations) {
								Node v = r.getOtherNode(nodeVDash);
								if (uLabel.equals(v.getProperty("name")) && v.getDegree() >= uDegree) {
									int vCnt = (int) v.getProperty("cnt");
									if (vCnt == CNT) {
										v.setProperty("cnt", vCnt + 1);
									}
								}
							}
						}
						// line14
						CNT++;
					}

					// line5-6
					ArrayList<Integer> removeNonCnd = new ArrayList<>();
					for (int v : C.get(u)) {
						Node nodeV = db.getNodeById((long) v);
						int vCnt = (int) nodeV.getProperty("cnt");
						if (vCnt != CNT) {
							// System.out.println("Removing Node-----");

							Node CPI_v = db.findNode(CPILabel, "nodeId", v);

							Iterable<Label> allLabels = CPI_v.getLabels();
							int labelCnt = 0;
							for (Label l : allLabels) {
								labelCnt++;
							}
							CPI_v.removeLabel(Label.label(Integer.toString(u)));
							removeNonCnd.add(v);
							// labelCnt = 2 because 1 for "CPI_Structure" and 1 for "u" itself
							// which we are removing
							// change if you add additional static labels
							if (labelCnt == 2) {	//delete relation first
								for (Relationship r : CPI_v.getRelationships()) {
									r.delete();
								}
								CPI_v.delete();	//delete node in Neo4j
							}
						}
					}
					C.get(u).removeAll(removeNonCnd);

					// line 23 reset vcnt
					ResourceIterator<Node> allNodes;
					allNodes = db.findNodes(Label.label(targetLabel));
					Node node;
					while (allNodes.hasNext()) {
						node = allNodes.next();
						node.setProperty("cnt", 0);
					}

					// line 8-11
					for (int v : C.get(u)) {
						// Because only bottoms onces are child and other niegh
						// are either siblings or parents
						for (int uDash : queryTree.get(u)) {
							if (u == rootNode || uDash != parents.get(u)) {
								Node CPI_v = db.findNode(CPILabel, "nodeId", v);
								for (Relationship r : CPI_v.getRelationships()) {
									Node CPI_vDash = r.getOtherNode(CPI_v);
									if (CPI_vDash.hasLabel(Label.label(Integer.toString(uDash)))) {
										if (!C.get(uDash).contains((int) CPI_vDash.getProperty("nodeId"))) {
											r.delete();
										}
									}
								}
							}
						}
					} // end for each candidate v
				}
			}
			// System.out.println("Reduced C: " + C);
			tx.success();
			tx.close();
		}

	}

	/**
	 * Perform subset match for CPI and ground truth result
	 * 
	 * @param targetLabel
	 * @param queryLabel
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void computeGroundTruthSubsetMatching(String targetLabel, String queryLabel) {
		File file = new File(groundTruthPath + "Proteins." + qd.noOfQueryNodes + ".gtr");
		HashMap<Integer, HashSet<Integer>> groundTruth = new HashMap<>();

		for (int i = 0; i < qd.noOfQueryNodes; i++) {
			groundTruth.put(i, new HashSet<Integer>());
		}

		int entriesMatch = 0;

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (line.equals("T:" + targetLabel + ".grf")) {
					line = br.readLine();
					if (line.equals("P:" + queryLabel + ".grf")) {
						line = br.readLine();
						entriesMatch = Integer.parseInt(line.substring(2));
						for (int i = 0; i < entriesMatch; i++) {
							line = br.readLine();
							String[] splitLine = line.split(":|;|,");

							HashMap<Integer, Integer> CPI_S = new HashMap<Integer, Integer>();
							for (int j = 2; j < splitLine.length - 1; j += 2) {
								// System.out.println(splitLine[j] + " " +
								// splitLine[j + 1]);
								groundTruth.get(Integer.parseInt(splitLine[j])).add(Integer.parseInt(splitLine[j + 1]));
								CPI_S.put(Integer.parseInt(splitLine[j]), Integer.parseInt(splitLine[j + 1]));
							}
							// System.out.println(CPI_S);
						}
						break;
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try (Transaction tx = db.beginTx()) {

			// verify with the CPI
			for (int i : qd.queryGraph.keySet()) {
				ResourceIterator<Node> allNodes;

				allNodes = db.findNodes(Label.label(Integer.toString(i)));
				ArrayList<Integer> originalIds = new ArrayList<>();
				while (allNodes.hasNext()) {
					Node n = allNodes.next();
					originalIds.add((int) n.getProperty("originalId"));
				}
				// System.out.println("Original Ids: u" + i + " " +
				// originalIds);
				List resultList = new ArrayList(groundTruth.get(i));
				Collections.sort(resultList);
				// System.out.println("Ground Truth: u" + i + " " + resultList);
				if (!originalIds.containsAll(groundTruth.get(i))) {
					System.out.println(">>> Ground Truth matching fails...\n");
					break;
				}
			}
			tx.success();
			tx.close();
		}
	}

	/**
	 * perforem CPI generate and store CPI result in the neo4j then evaluate CPI
	 * result with ground truth file
	 * 
	 * @param queryDecomposed
	 * @param targetFile
	 * @param queryFile
	 * @param querySize
	 */
	void startCPIGenration(CPI_QueryDecomposition queryDecomposed, File targetFile, File queryFile, int querySize) {
		qd = queryDecomposed;
		n = querySize;

		String targetLabel = FilenameUtils.removeExtension(targetFile.getName());
		String queryLabel = FilenameUtils.removeExtension(queryFile.getName());

		deleteCPIStructure();

		computeCandidateSet(targetLabel);

		createQueryTree();

		topDownCPI_Construct(targetLabel);

		bottomUpCPI_Refinement(targetLabel);

		computeGroundTruthSubsetMatching(targetLabel, queryLabel);
	}
}
