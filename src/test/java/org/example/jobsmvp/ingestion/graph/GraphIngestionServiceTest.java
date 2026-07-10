package org.example.jobsmvp.ingestion.graph;

import org.example.jobsmvp.ingestion.transform.JobGraphBundle;
import org.example.jobsmvp.models.nodes.Company;
import org.example.jobsmvp.models.nodes.Job;
import org.example.jobsmvp.models.nodes.Occupation;
import org.example.jobsmvp.models.nodes.Skill;
import org.example.jobsmvp.models.relationships.Posts;
import org.example.jobsmvp.models.relationships.Requires;
import org.example.jobsmvp.repositories.CompanyRepository;
import org.example.jobsmvp.repositories.JobRepository;
import org.example.jobsmvp.repositories.OccupationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GraphIngestionServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private OccupationRepository occupationRepository;

    // Use deep stubs for the client to handle the fluent API chain.
    private Neo4jClient neo4jClient;

    @Mock
    private Neo4jClient.RunnableSpec runnableSpec;

    private GraphIngestionService graphIngestionService;

    private Company company;
    private Job job;
    private Occupation occupation;
    private Skill skill;
    private JobGraphBundle bundle;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Initialize the Neo4jClient mock with deep stubs.
        neo4jClient = mock(Neo4jClient.class, RETURNS_DEEP_STUBS);

        // Manually construct the service with the mocks.
        graphIngestionService = new GraphIngestionService(
                companyRepository,
                jobRepository,
                occupationRepository,
                neo4jClient
        );

        // Mock the full fluent API chain.
        when(neo4jClient.query(anyString()).bindAll(anyMap())).thenReturn(runnableSpec);

        company = new Company();
        company.setCompany_id(UUID.randomUUID().toString());
        company.setName("Test Company");
        company.setTextEmbedding(List.of(1.0)); // Add embedding to test that path

        job = new Job();
        job.setJob_id(UUID.randomUUID().toString());
        job.setTitle("Test Job");

        occupation = new Occupation();
        occupation.setOccupationId(UUID.randomUUID().toString());
        occupation.setName("Test Occupation");

        skill = new Skill();
        skill.setSkillId(UUID.randomUUID().toString());
        skill.setName("Test Skill");
        skill.setParent("Test Skill Group");

        bundle = new JobGraphBundle(
                company, job, new Posts(), occupation, List.of(skill), List.of(new Requires())
        );

        when(companyRepository.findByCompanyId(company.getCompany_id())).thenReturn(Optional.of(company));
        when(jobRepository.findByJobId(job.getJob_id())).thenReturn(Optional.empty());
        when(jobRepository.save(any(Job.class))).thenReturn(job);
    }

    @Test
    void testIngest_allPaths() {
        // When
        graphIngestionService.ingest(bundle);

        // Then
        // Verify the final 'run' method is called for each query.
        // The total count is 9:
        // 2 for Company (merge + embedding)
        // 1 for Posts relationship
        // 1 for Occupation
        // 1 for Has_Occupation relationship
        // 1 for Skill
        // 1 for Requires relationship
        // 2 for SkillHierarchy (skill->group, group->category)
        verify(runnableSpec, times(9)).run();
    }
}