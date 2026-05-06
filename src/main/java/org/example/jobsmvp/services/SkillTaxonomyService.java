package org.example.jobsmvp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.jobsmvp.models.nodes.Skill;
import org.example.jobsmvp.repositories.SkillRepository;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillTaxonomyService {

    private final SkillRepository skillRepository;
    private final Neo4jClient neo4jClient;

    /**
     * Finds all layer-3 skills that lack a SUBCLASS_OF relationship
     * and links them to the correct layer-2 Skill Group based on their 'parent' property.
     */
    @Transactional
    public int fixMissingSubclassOfRelations() {
        List<Skill> orphanedSkills = skillRepository.findSkillsWithoutSubclassOf();
        int fixedCount = 0;

        for (Skill skill : orphanedSkills) {
            if (skill.getParent() != null && !skill.getParent().isBlank()) {
                boolean linked = linkSkillToParent(skill.getSkillId(), skill.getParent());
                if (linked) {
                    fixedCount++;
                }
            } else {
                log.warn("Skill '{}' (ID: {}) has no SUBCLASS_OF relation and no parent name set.", skill.getName(), skill.getSkillId());
            }
        }

        log.info("Fixed {} missing SUBCLASS_OF relationships.", fixedCount);
        return fixedCount;
    }

    private boolean linkSkillToParent(String skillId, String parentName) {
        var result = neo4jClient.query("""
                MATCH (child:Skill {skill_id: $skillId, layer: 3})
                MATCH (parent:Skill {name: $parentName, layer: 2})
                MERGE (child)-[r:SUBCLASS_OF]->(parent)
                RETURN count(r) as relCount
                """)
                .bindAll(Map.of(
                        "skillId", skillId,
                        "parentName", parentName
                ))
                .fetch()
                .one();
        
        return result.isPresent() && ((Long) result.get().get("relCount")) > 0;
    }
}
