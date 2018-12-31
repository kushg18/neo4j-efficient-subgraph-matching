Neo4j Efficient Subgraph Matching using CPI construction
========================================================

### About ###
-----------------------------
This project is about learning and implementing an algorithm to perform ***CPI construction*** and also perform efficient subgraph matching using the CPI. This postpones the cartesian product that takes place while searching for nodes with its relationships in the huge target graph by reducing the target graph based on the requirements of the given subgraph. 

### Technology Stack ### 
-----------------------------
1. Java
2. Cypher Query
2. Eclipse
3. Neo4j Graph Database
4. Neo4j IDE Community Version
5. Graph Database Service

### Comparison ###
-----------------------------
Given below is the comparison made between performing matching on the whole subgraph and performing matching using CPI construction. Here X-axis represents the size of the subgraph (For e.g. protein 8 means subgraph with 8 nodes and relationships between them) and Y-axis represents the time it takes to perform the matching.
 - Obviously, it takes a lot longer to perform matching using CPI construction, but the benefit of using it is saving the time in the future. 
 - Same constructed CPI can be used in future and it will definitely reduce the time for subgraph matching as compared to the later one.

Average time comparison with different protein size - 

![alt text](https://github.com/kushg18/neo4j-efficient-subgraph-matching/blob/master/screenshot/comparison.png)