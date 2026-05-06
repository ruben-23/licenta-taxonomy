//package org.example.jobsmvp.repositories;
//
//import org.example.jobsmvp.models.nodes.Skill;
//import org.springframework.data.neo4j.repository.Neo4jRepository;
//import org.springframework.data.neo4j.repository.query.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//
//import java.util.Optional;
//
//@Repository
//public interface SkillRepository extends Neo4jRepository<Skill, String> {
//
//    @Query("MATCH (s:Skill) WHERE toLower(s.name) = toLower($name) RETURN s LIMIT 1")
//    Optional<Skill> findByNameIgnoreCase(@Param("name") String name);
//
//    @Query("MATCH (s:Skill) WHERE s.skill_id = $skillId RETURN s LIMIT 1")
//    Optional<Skill> findBySkillId(@Param("skillId") String skillId);
//
//    @Query("""
//            MATCH (s:Skill)
//            WHERE s.text_embedding IS NOT NULL
//            WITH s,
//                 gds.similarity.cosine(s.text_embedding, $queryVector) AS score
//            WHERE score >= $threshold
//            RETURN s
//            ORDER BY score DESC
//            LIMIT 1
//            """)
//    Optional<Skill> findMostSimilarSkill(
//            @Param("queryVector") double[] queryVector,
//            @Param("threshold") double threshold
//    );
//
//    boolean existsBySkillId(String skillId);
//}

package org.example.jobsmvp.repositories;

import org.example.jobsmvp.models.nodes.Skill;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillRepository extends Neo4jRepository<Skill, String> {

    /** Case-insensitive name lookup across all layers. */
    @Query("MATCH (s:Skill) WHERE toLower(s.name) = toLower($name) RETURN s LIMIT 1")
    Optional<Skill> findByNameIgnoreCase(@Param("name") String name);

    /** Lookup scoped to a specific taxonomy layer (avoids name collisions across layers). */
    @Query("MATCH (s:Skill) WHERE toLower(s.name) = toLower($name) AND s.layer = $layer RETURN s LIMIT 1")
    Optional<Skill> findByNameIgnoreCaseAndLayer(@Param("name") String name, @Param("layer") int layer);

    @Query("MATCH (s:Skill) WHERE s.skill_id = $skillId RETURN s LIMIT 1")
    Optional<Skill> findBySkillId(@Param("skillId") String skillId);

    /** Returns all Skill nodes at the given taxonomy layer. */
    @Query("MATCH (s:Skill) WHERE s.layer = $layer RETURN s")
    List<Skill> findAllByLayer(@Param("layer") int layer);

    @Query("""
            MATCH (s:Skill)
            WHERE s.text_embedding IS NOT NULL
              AND s.layer = 3
            WITH s,
                 gds.similarity.cosine(s.text_embedding, $queryVector) AS score
            WHERE score >= $threshold
            RETURN s
            ORDER BY score DESC
            LIMIT 1
            """)
    Optional<Skill> findMostSimilarSkill(
            @Param("queryVector") double[] queryVector,
            @Param("threshold") double threshold
    );

    @Query("RETURN EXISTS { MATCH (s:Skill) WHERE toLower(s.name) = toLower($name) } AS result")
    boolean existsByNameIgnoreCase(@Param("name") String name);

    boolean existsBySkillId(String skillId);

    @Query("MATCH (s:Skill) WHERE NOT (s)-[:SUBCLASS_OF]->() AND s.layer = 3 RETURN s")
    List<Skill> findSkillsWithoutSubclassOf();
}