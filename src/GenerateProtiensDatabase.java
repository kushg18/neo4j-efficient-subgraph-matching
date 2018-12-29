/**
 *CS729 Group Project
 * GenerateProtiensDatabase.java
 * This program generate the protein database in neo4j
 * Author:
 * Gevaria, Harnisha (hgg5350@g.rit.edu)
 * Gevaria, Kushal (kgg5247@rit.edu)
 * Ku, Wei-Yao (wxk6489@rit.edu)
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.io.FilenameUtils;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.unsafe.batchinsert.BatchInserter;
import org.neo4j.unsafe.batchinsert.BatchInserters;

public class GenerateProtiensDatabase {

	//User specify file path here
	//for Mac
	static String path = "./Proteins/Proteins/target/";
	static String dbath = "./db/ProtienForProject/";

	//for Windows
	//static String path = "C:\\Harnisha\\Rochester Institute of Technology\\Courses\\Graph Database\\Assignments\\Proteins\\Proteins\\target\\";
	//static String dbath = "C:/Users/HaRnIsHa/Documents/Neo4j/ProtienForProject";

	File[] listOfFiles;
	BatchInserter bi;

	Map<String, Double> nodeProb = new HashMap<String, Double>();
	Map<Integer, Double> degreeProb = new TreeMap<Integer, Double>(Collections.reverseOrder());

	/**
	 * Open the batch inserter at the given locations to insert the data
	 */
	private void openBatchInserter() {
		try {
			bi = BatchInserters.inserter(new File(dbath));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Close batch inserter, when we do this it pushes all the data together to
	 * the database
	 */
	private void closeBatchInserter() {
		bi.shutdown();
	}

	/**
	 * Read File and insert the nodes with profiles and create graphs
	 * 
	 * @param file
	 * @param additionValue
	 * @param profile
	 */
	private void readFileAndInsert(File file, int additionValue, Map<Integer, String[]> profile) {

		Map<String, Object> attributes;
		Label graphLabel = Label.label(FilenameUtils.removeExtension(file.getName()));
		Label probLabel = Label.label("VF2_PlusMetaData");
		boolean metaDataDone = false;
		int id;

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			String input[];

			line = br.readLine();
			int noOfNodes = Integer.parseInt(line);
			System.out.println("Loading " + noOfNodes);
			for (int i = 0; i < noOfNodes; i++) {

				input = br.readLine().split(" ");
				id = Integer.parseInt(input[0]);
				// System.out.println(input[0] + input[1]);

				attributes = new HashMap<String, Object>();
				attributes.put("id", id);
				attributes.put("name", input[1]);
				attributes.put("profile", profile.get(id));
				attributes.put("cnt", (int) 0);

				if (metaDataDone) {
					bi.createNode(id + additionValue, attributes, graphLabel);
				} else {
					for (Entry<String, Double> entry : nodeProb.entrySet()) {
						attributes.put(entry.getKey(), entry.getValue());
					}
					for (Entry<Integer, Double> entry : degreeProb.entrySet()) {
						attributes.put(entry.getKey().toString(), entry.getValue());
					}
					// System.out.println(attributes);
					bi.createNode(id + additionValue, attributes, graphLabel, probLabel);
					metaDataDone = true;
				}
			}

			int noOfEdges;
			int fromId, toId;
			RelationshipType relation = RelationshipType.withName("con");

			while ((line = br.readLine()) != null) {
				noOfEdges = Integer.parseInt(line);
				for (int i = 0; i < noOfEdges; i++) {
					input = br.readLine().split(" ");
					fromId = Integer.parseInt(input[0]) + additionValue;
					toId = Integer.parseInt(input[1]) + additionValue;
					// System.out.println(fromId+ " " + toId);
					if (fromId < toId) {
						bi.createRelationship(fromId, toId, relation, null);
					}
				}
				// break;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create profile for each node
	 * 
	 * @param file
	 * @return
	 */
	private Map<Integer, String[]> getProfile(File file) {
		Map<Integer, String> nodeList = new HashMap<Integer, String>();
		Map<Integer, String[]> nodeProfileList = new HashMap<Integer, String[]>();
		String[] nodeProfile;

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			String input[];

			line = br.readLine();
			int noOfNodes = Integer.parseInt(line);
			System.out.println("Loading " + noOfNodes);
			for (int i = 0; i < noOfNodes; i++) {
				input = br.readLine().split(" ");
				nodeList.put(Integer.parseInt(input[0]), input[1]);
			}

			int noOfEdges;

			for (int i = 0; i < noOfNodes; i++) {
				line = br.readLine();
				noOfEdges = Integer.parseInt(line);
				nodeProfile = new String[noOfEdges + 1];
				nodeProfile[0] = nodeList.get(i);
				for (int j = 1; j <= noOfEdges; j++) {
					input = br.readLine().split(" ");
					nodeProfile[j] = nodeList.get(Integer.parseInt(input[1]));
				}
				// sorting alphabetically
				Arrays.sort(nodeProfile);
				nodeProfileList.put(i, nodeProfile);
			}
			// System.out.println(nodeProfileList);

		} catch (IOException e) {
			e.printStackTrace();
		}
		return nodeProfileList;
	}

	private void getProbabilityInfo(File file) {
		nodeProb = new HashMap<String, Double>();
		degreeProb = new TreeMap<Integer, Double>(Collections.reverseOrder());

		int noOfNodes = 0;

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			String input[];

			line = br.readLine();
			noOfNodes = Integer.parseInt(line);
			System.out.println("Loading " + noOfNodes);
			for (int i = 0; i < noOfNodes; i++) {
				input = br.readLine().split(" ");
				nodeProb.put(input[1], nodeProb.getOrDefault(input[1], (double) 0) + 1);
			}
			// System.out.println(nodeProb);

			int noOfEdges;

			for (int i = 0; i < noOfNodes; i++) {
				line = br.readLine();
				noOfEdges = Integer.parseInt(line);

				degreeProb.put(noOfEdges, degreeProb.getOrDefault(noOfEdges, (double) 0) + 1);

				for (int j = 1; j <= noOfEdges; j++) {
					input = br.readLine().split(" ");
				}
			}
			// System.out.println(degreeProb);

		} catch (IOException e) {
			e.printStackTrace();
		}

		// calculating probability

		double addPrviousDegree = 0;
		for (Entry<Integer, Double> entry : degreeProb.entrySet()) {
			entry.setValue((entry.getValue() / noOfNodes) + addPrviousDegree);
			addPrviousDegree = entry.getValue();
		}

		for (Entry<String, Double> entry : nodeProb.entrySet()) {
			entry.setValue(entry.getValue() / noOfNodes);
		}

		// System.out.println(nodeProb);
		// System.out.println(degreeProb);
	}

	private void getAllFileNames() {
		File folder = new File(path);
		listOfFiles = folder.listFiles();
		System.out.println(listOfFiles.length);

		for (int i = 0; i < listOfFiles.length; i++) {
			// System.out.println("File " +
			// FilenameUtils.removeExtension(listOfFiles[i].getName()));
			// protienDic.put(listOfFiles[i].getName(), i * 11000);
			Map<Integer, String[]> profile = getProfile(listOfFiles[i]);
			getProbabilityInfo(listOfFiles[i]);
			readFileAndInsert(listOfFiles[i], i * 11000, profile);
		}
		// readFileAndInsert(listOfFiles[0], 11000);
	}



	public static void main(String[] args) {
		GenerateProtiensDatabase lp = new GenerateProtiensDatabase();
		lp.openBatchInserter();

		lp.getAllFileNames();

		// lp.singleFile();

		lp.closeBatchInserter();
	}
}
