package org.example.jobsmvp.repositories;

import org.example.jobsmvp.models.nodes.Technology;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TechnologyRepository extends Neo4jRepository<Technology, String> {

    /**
     * Brute-force cosine similarity using Neo4j's native vector math function.
     */
    @Query("""
        MATCH (t:Technology)
        WHERE t.text_embedding IS NOT NULL
        WITH t, vector.similarity.cosine($embedding, t.text_embedding) AS score
        WHERE score >= $threshold
        RETURN t ORDER BY score DESC LIMIT 1
        """)
    Technology findSimilarTechnology(@Param("embedding") List<Float> embedding, @Param("threshold") double threshold);

    Technology findByName(String name);

    /**
     * Case-insensitive Technology lookup — used by EntityNormalizationService.
     */
    @Query("MATCH (t:Technology) WHERE toLower(t.name) = toLower($name) RETURN t LIMIT 1")
    Optional<Technology> findTechnologyByNameIgnoreCase(@Param("name") String name);

    /**
     * Returns all Technology nodes for embedding similarity matching.
     * Result is cached in-memory inside EntityNormalizationService per run.
     */
    @Query("MATCH (t:Technology) RETURN t")
    List<Technology> findAllTechnologies();

    /**
     * Index-backed vector search
     */
    @Query("""
        CALL db.index.vector.queryNodes('tech_embeddings', 1, $queryVector) 
        YIELD node AS t, score AS similarity
        WHERE similarity >= $threshold
        RETURN t
    """)
    Optional<Technology> findMostSimilarTechnology(
            @Param("queryVector") double[] queryVector,
            @Param("threshold") double threshold
    );
}