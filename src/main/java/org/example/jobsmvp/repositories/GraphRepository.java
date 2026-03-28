package org.example.jobsmvp.repositories;


import org.example.jobsmvp.models.Graph;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
@Repository
public interface GraphRepository extends Neo4jRepository<Graph, Long> {

    // --- STEP 1: Project the graph into memory ---
    @Query("""
        CALL gds.graph.project(
            'myNode2VecGraph',
            '*',
            { ALL_RELS: { type: '*', orientation: 'UNDIRECTED' } }
        ) YIELD nodeCount, relationshipCount
        RETURN nodeCount
    """)
    Long createGraphProjection();

    // --- STEP 2: Run the algorithm on the projected graph ---
    @Query("""
        CALL gds.node2vec.write('myNode2VecGraph', {
            embeddingDimension: 128,
            walkLength: 80,
            walksPerNode: 10,
            inOutFactor: 1.0,
            returnFactor: 1.0,
            writeProperty: 'embedding'
        })
        YIELD nodePropertiesWritten
        RETURN nodePropertiesWritten
    """)
    Long writeNode2VecEmbeddings();

    // --- STEP 3: Drop the graph from memory ---
    @Query("""
        CALL gds.graph.drop('myNode2VecGraph', false) YIELD graphName
        RETURN 1
    """)
    Long dropGraphProjection();


    // --- VECTOR INDEXES  ---
    @Query("""
        CREATE VECTOR INDEX student_embeddings IF NOT EXISTS 
        FOR (s:Student) ON (s.embedding) 
        OPTIONS {indexConfig: {
            `vector.dimensions`: 128, 
            `vector.similarity_function`: 'cosine'
        }}
    """)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void createStudentVectorIndex();

    @Query("""
        CREATE VECTOR INDEX job_embeddings IF NOT EXISTS 
        FOR (j:Job) ON (j.embedding) 
        OPTIONS {indexConfig: {
            `vector.dimensions`: 128, 
            `vector.similarity_function`: 'cosine'
        }}
    """)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void createJobVectorIndex();

    @Query("""
        CREATE VECTOR INDEX tech_embeddings IF NOT EXISTS 
        FOR (t:Technology) ON (t.text_embedding) 
        OPTIONS {indexConfig: {
            `vector.dimensions`: 768, 
            `vector.similarity_function`: 'cosine'
        }}
    """)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void createTechnologyVectorIndex();
}