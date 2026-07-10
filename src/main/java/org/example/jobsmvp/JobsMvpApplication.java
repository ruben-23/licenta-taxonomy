package org.example.jobsmvp;

import org.example.jobsmvp.repositories.GraphRepository;
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

		return args -> {

			// 1. Create vector indices
			try {
				System.out.println("Initializing vector indexes...");
//				graphRepository.createStudentVectorIndex();
//				graphRepository.createJobVectorIndex();
				graphRepository.createTechnologyVectorIndex();
				System.out.println("Vector indexes initialized successfully.");
			} catch (Exception e) {
				System.err.println("Failed to create vector indexes.");
				e.printStackTrace();
			}

			// 2. Generate Node2Vec embeddings
//			try {
//				System.out.println("Generating Node2Vec embeddings...");
//
//				// Step 0: Clean up any old graph projection that might be stuck in memory
//				graphRepository.dropGraphProjection();
//
//				// Step 1: Project the graph into GDS memory
//				Long projectedNodes = graphRepository.createGraphProjection();
//				System.out.println("Projected " + projectedNodes + " nodes into GDS memory.");
//
//				// Step 2: Run the algorithm and write properties back to the database
//				Long nodesProcessed = graphRepository.writeNode2VecEmbeddings();
//				System.out.println("Embeddings generated and written for " + nodesProcessed + " nodes.");
//
//				// Step 3: Drop the graph from memory
//				graphRepository.dropGraphProjection();
//				System.out.println("Cleaned up GDS memory.");
//
//			} catch (Exception e) {
//				System.err.println("Failed to generate embeddings.");
//				e.printStackTrace();
//			}
		};
	}

	@Bean
	WebClient.Builder webClientBuilder() {
		return WebClient.builder();
	}

}
