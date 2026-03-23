package org.example.jobsmvp.repositories;

import org.example.jobsmvp.models.Graph;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface GraphRepository extends Neo4jRepository<Graph, Long> {

    /**
     * Triggers the Memgraph MAGE Node2Vec algorithm across the entire graph.
     * * Parameters:
     * - is_directed: False (walks traverse edges in both directions)
     * - p: 1.0 (Return hyperparameter)
     * - q: 1.0 (Inout hyperparameter)
     * - num_walks: 10 (Walks per node)
     * - walk_length: 80 (Steps per walk)
     * - vector_size: 128 (Dimensions of the resulting embedding array)
     * * @return The total number of nodes that received an embedding.
     */
    @Query("""
        CALL node2vec.set_embeddings(False, 1.0, 1.0, 10, 80, 128) 
        YIELD node 
        RETURN count(node)
    """)
    Long generateNode2VecEmbeddings();


    @Query("""
        CREATE VECTOR INDEX student_embeddings ON :Student(embedding) WITH CONFIG {"dimension": 128, "metric": "cos", "capacity": 1000}
    """)
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // <-- disables transaction
    void createStudentVectorIndex();

    @Query("""
        CREATE VECTOR INDEX job_embeddings ON :Job(embedding) WITH CONFIG {"dimension": 128, "metric": "cos", "capacity": 1000}
    """)
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // <-- disables transaction
    void createJobVectorIndex();

    /**
     * Creates a vector index for Technology nodes.
     */
    @Query("""
        CREATE VECTOR INDEX tech_embeddings ON :Technology(text_embedding) 
        WITH CONFIG {"dimension": 768, "metric": "cos", "capacity": 1000}
    """)
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // <-- disables transaction
    void createTechnologyVectorIndex();

}