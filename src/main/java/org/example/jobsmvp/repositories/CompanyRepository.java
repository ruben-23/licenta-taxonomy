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

    @Query("MATCH (c:Company {company_id: $companyId}) RETURN COUNT(c) > 0")
    boolean existsByCompanyId(@Param("companyId") String companyId);

}