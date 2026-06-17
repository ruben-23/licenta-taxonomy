package org.example.jobsmvp.repositories;
import org.example.jobsmvp.models.nodes.Company;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends Neo4jRepository<Company, String> {
    @Query("MATCH (c:Company) RETURN c SKIP $skip LIMIT $limit")
    List<Company> findAllPaginated(int skip, int limit);

    @Query("MATCH (c:Company {company_id: $companyId}) RETURN c LIMIT 1")
    Optional<Company> findByCompanyId(@Param("companyId") String companyId);
    
    @Query("MATCH (c:Company) WHERE toLower(c.name) = toLower($name) RETURN c LIMIT 1")
    Optional<Company> findByNameIgnoreCase(@Param("name") String name);

//    @Query("""
//            CALL db.index.vector.queryNodes('company_text_embeddings', 1, $embedding)
//            YIELD node, score
//            WHERE score >= $threshold
//            RETURN node LIMIT 1
//            """)
    @Query("""
        MATCH (c:Company)
        WHERE c.text_embedding IS NOT NULL
    
        WITH c,
             gds.similarity.cosine(c.text_embedding, $embedding) AS score
    
        WHERE score >= $threshold
    
        RETURN c
        ORDER BY score DESC
        LIMIT 1
        """)
    Optional<Company> findMostSimilarCompany(@Param("embedding") double[] embedding, @Param("threshold") double threshold);

    @Query("MATCH (c:Company {company_id: $companyId}) RETURN COUNT(c) > 0")
    boolean existsByCompanyId(@Param("companyId") String companyId);

}