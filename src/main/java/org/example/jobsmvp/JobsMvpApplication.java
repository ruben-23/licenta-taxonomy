package org.example.jobsmvp;

import org.example.jobsmvp.repositories.GraphRepository;
import org.neo4j.driver.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
public class JobsMvpApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobsMvpApplication.class, args);
	}

	@Bean
	CommandLineRunner runEmbeddings(GraphRepository graphRepository) {

		return 	args -> {

			// 1. Create vector indices using Neo4j Driver (auto-commit)
			try (Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("", ""));) {
				createVectorIndex(driver, "Student", "embedding", 128, "student_embeddings");
				createVectorIndex(driver, "Job", "embedding", 128, "job_embeddings");
				createVectorIndex(driver, "Technology", "text_embedding", 768, "tech_embeddings");
			} catch (Exception e) {
				e.printStackTrace();
			}

			// 2. Generate Node2Vec embeddings via repository
			Long nodesProcessed = graphRepository.generateNode2VecEmbeddings();
			System.out.println("Embeddings generated for " + nodesProcessed + " nodes.");
		};
	}

	private void createVectorIndex(Driver driver, String label, String property, int dimension, String indexName) {
		String cypher = String.format(
				"CREATE VECTOR INDEX %s ON :%s(%s) WITH CONFIG {\"dimension\": %d, \"metric\": \"cos\", \"capacity\": 1000}",
				indexName, label, property, dimension
		);

		try (Session session = driver.session(SessionConfig.defaultConfig())) { // auto-commit
			session.run(cypher);
			System.out.println("Created vector index: " + indexName);
		} catch (Exception e) {
			System.out.println("Index '" + indexName + "' might already exist or failed to create.");
			e.printStackTrace();
		}
	}


	@Bean
	WebClient.Builder webClientBuilder() {
		return WebClient.builder();
	}

}
