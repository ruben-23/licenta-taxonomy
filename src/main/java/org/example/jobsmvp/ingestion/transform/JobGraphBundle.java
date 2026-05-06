//package org.example.jobsmvp.ingestion.transform;
//
//import org.example.jobsmvp.models.nodes.Company;
//import org.example.jobsmvp.models.nodes.Job;
//import org.example.jobsmvp.models.nodes.Skill;
//import org.example.jobsmvp.models.relationships.Posts;
//import org.example.jobsmvp.models.relationships.Requires;
//
//import java.util.List;
//
///**
// * Carries all graph nodes and relationships produced for a single job posting
// * from the {@link GraphTransformService}.
// *
// * Passed as a unit to {@link org.example.jobsmvp.ingestion.graph.GraphIngestionService}
// * for persistence.
// */
//public record JobGraphBundle(
//        Company company,
//        Job job,
//        Posts postsRelationship,
//        List<Skill> technologies,
//        List<Requires> requiresRelationships
//) {}


package org.example.jobsmvp.ingestion.transform;

import org.example.jobsmvp.models.nodes.Company;
import org.example.jobsmvp.models.nodes.Job;
import org.example.jobsmvp.models.nodes.Occupation;
import org.example.jobsmvp.models.nodes.Skill;
import org.example.jobsmvp.models.relationships.Posts;
import org.example.jobsmvp.models.relationships.Requires;

import java.util.List;

/**
 * Carries all graph nodes and relationships produced for a single job posting
 * from the {@link GraphTransformService}.
 *
 * Passed as a unit to {@link org.example.jobsmvp.ingestion.graph.GraphIngestionService}
 * for persistence.
 *
 * Changes from previous version:
 *  - {@code List<Technology> technologies} → {@code List<Skill> skills}
 *  - {@code Occupation occupation} added (may be null if extraction failed)
 */
public record JobGraphBundle(
        Company company,
        Job job,
        Posts postsRelationship,
        Occupation occupation,
        List<Skill> skills,
        List<Requires> requiresRelationships
) {}