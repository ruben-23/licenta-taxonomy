package org.example.jobsmvp.repositories;

import org.example.jobsmvp.models.nodes.Occupation;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OccupationRepository extends Neo4jRepository<Occupation, String> {

    @Query("MATCH (o:Occupation) WHERE toLower(o.name) = toLower($name) RETURN o LIMIT 1")
    Optional<Occupation> findByNameIgnoreCase(@Param("name") String name);

    @Query("MATCH (o:Occupation) WHERE o.occupation_id = $occupationId RETURN o LIMIT 1")
    Optional<Occupation> findByOccupationId(@Param("occupationId") String occupationId);

    @Query("""
            MATCH (o:Occupation)
            WHERE o.text_embedding IS NOT NULL
            WITH o,
                 gds.similarity.cosine(o.text_embedding, $queryVector) AS score
            WHERE score >= $threshold
            RETURN o
            ORDER BY score DESC
            LIMIT 1
            """)
    Optional<Occupation> findMostSimilarOccupation(
            @Param("queryVector") double[] queryVector,
            @Param("threshold") double threshold
    );

    boolean existsByOccupationId(String occupationId);
}