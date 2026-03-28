import json
from neo4j import GraphDatabase

# Aici definim clasa care va gestiona conexiunea și inserarea datelor
class KnowledgeGraphLoader:
    def __init__(self, uri, user, password):
        self.driver = GraphDatabase.driver(uri, auth=(user, password))

    def close(self):
        self.driver.close()

    def insert_job(self, job_data):
        with self.driver.session() as session:
            session.execute_write(self._create_job_tx, job_data["job"])
            print(f"Jobul '{job_data['job']['title']}' a fost inserat cu succes!")

    def insert_student(self, student_data):
        with self.driver.session() as session:
            session.execute_write(self._create_student_tx, student_data["student"])
            print(f"Studentul '{student_data['student']['name']}' a fost inserat cu succes!")

    # --- Tranzacția pentru Job ---
    @staticmethod
    def _create_job_tx(tx, job):
        # 1. Creăm Compania și Jobul
        query_job = """
        MERGE (c:Company {company_id: $company.company_id})
        SET c.name = $company.name, c.industry = $company.industry, 
            c.location = $company.location, c.size = $company.size
            
        MERGE (j:Job {job_id: $job_id})
        SET j.title = $title, j.experience_level = $exp_level, j.job_type = $job_type,
            j.contract_duration = $contract_duration, j.description = $description,
            j.salary = $salary, j.currency = $currency, j.remote = $remote,
            j.location = $location, j.posted_date = $posted_date, j.expires_at = $expires_at
            
        MERGE (c)-[:POSTS {is_active: true}]->(j)
        """
        tx.run(query_job, 
               company=job["company"], job_id=job["job_id"], title=job["title"], 
               exp_level=job["experience_level"], job_type=job["job_type"], 
               contract_duration=job["contract_duration"], description=job["description"], 
               salary=job["salary"], currency=job["currency"], remote=job["remote"], 
               location=job["location"], posted_date=job["posted_date"], expires_at=job["expires_at"])

        # 2. Creăm Tehnologiile (MATCH-ul pentru Job este pus la început acum!)
        query_tech = """
        MATCH (j:Job {job_id: $job_id})
        WITH j
        UNWIND $technologies AS tech
        MERGE (t:Technology {tech_id: tech.tech_id})
        SET t.name = tech.name, t.category = tech.category
        
        MERGE (j)-[r:REQUIRES]->(t)
        SET r.importance = tech.importance, r.min_proficiency = tech.min_proficiency
        """
        tx.run(query_tech, technologies=job["requires_technologies"], job_id=job["job_id"])

    # --- Tranzacția pentru Student ---
    @staticmethod
    def _create_student_tx(tx, student):
        # 1. Creăm Studentul
        query_student = """
        MERGE (s:Student {student_id: $student_id})
        SET s.name = $name, s.major = $major, s.graduation_year = $grad_year,
            s.current_year_of_study = $year_of_study, s.degree_level = $degree
        """
        tx.run(query_student, student_id=student["student_id"], name=student["name"], 
               major=student["major"], grad_year=student["graduation_year"], 
               year_of_study=student["current_year_of_study"], degree=student["degree_level"])

        # 2. Tehnologiile (KNOWS) - MATCH pus la început
        query_knows = """
        MATCH (s:Student {student_id: $student_id})
        WITH s
        UNWIND $known_techs AS tech
        MERGE (t:Technology {tech_id: tech.tech_id})
        SET t.name = tech.name
        MERGE (s)-[k:KNOWS]->(t)
        SET k.proficiency_level = tech.proficiency_level, k.years_of_experience = tech.years_of_experience
        """
        tx.run(query_knows, known_techs=student["known_technologies"], student_id=student["student_id"])

        # 3. Proiectele (MATCH pus la început și WITH adăugat corect)
        if "projects" in student and student["projects"]:
            query_projects = """
            MATCH (s:Student {student_id: $student_id})
            WITH s
            UNWIND $projects AS proj
            MERGE (p:Project {project_id: proj.project_id})
            SET p.title = proj.title, p.description = proj.description, p.github_link = proj.github_link
            MERGE (s)-[:CREATED]->(p)
            
            WITH p, proj
            UNWIND proj.built_with AS tech_id
            MATCH (t:Technology {tech_id: tech_id})
            MERGE (p)-[:BUILT_WITH]->(t)
            """
            tx.run(query_projects, projects=student["projects"], student_id=student["student_id"])

        # 4. Cursurile
        if "courses" in student and student["courses"]:
            query_courses = """
            MATCH (s:Student {student_id: $student_id})
            WITH s
            UNWIND $courses AS crs
            MERGE (c:Course {course_id: crs.course_id})
            SET c.title = crs.title, c.description = crs.description, c.provider = crs.provider
            MERGE (s)-[:COMPLETED]->(c)
            
            WITH c, crs
            UNWIND crs.covers AS tech_id
            MATCH (t:Technology {tech_id: tech_id})
            MERGE (c)-[:COVERS]->(t)
            """
            tx.run(query_courses, courses=student["courses"], student_id=student["student_id"])

        # 5. Diplomele
        if "diplomas" in student and student["diplomas"]:
            query_diplomas = """
            MATCH (s:Student {student_id: $student_id})
            WITH s
            UNWIND $diplomas AS dip
            MERGE (d:Diploma {diploma_id: dip.diploma_id})
            SET d.title = dip.title, d.description = dip.description, d.issuer = dip.issuer
            MERGE (s)-[:EARNED]->(d)
            
            WITH d, dip
            UNWIND dip.certifies AS tech_id
            MATCH (t:Technology {tech_id: tech_id})
            MERGE (d)-[:CERTIFIES]->(t)
            """
            tx.run(query_diplomas, diplomas=student["diplomas"], student_id=student["student_id"])


# --- Rularea Scriptului ---
if __name__ == "__main__":
    # 1. Citim și parsăm corect fișierele JSON
    try:
        with open("./job.json", "r", encoding="utf-8") as f:
            job_json = json.load(f) # json.load transformă fișierul text într-un dicționar Python
            
        with open("./student.json", "r", encoding="utf-8") as f:
            student_json = json.load(f)
    except FileNotFoundError as e:
        print(f"Eroare: Nu am găsit fișierul! Asigură-te că ai creat job.json și student.json în același folder cu main.py.\nDetalii: {e}")
        exit(1)
    except json.JSONDecodeError as e:
        print(f"Eroare: JSON-ul nu este valid (poate lipsește o virgulă sau o acoladă?).\nDetalii: {e}")
        exit(1)

    # 2. Conexiunea pentru Memgraph
    URI = "bolt://localhost:7687"
    USER = "neo4j"
    PASSWORD = "Test1234"

    # 3. Inserarea datelor
    try:
        loader = KnowledgeGraphLoader(URI, USER, PASSWORD)

        # Iterăm prin lista de joburi
        for job in job_json.get("jobs", []):
            loader.insert_job({"job": job}) # Îl împachetăm înapoi în dicționarul cerut de funcție
            
        # Iterăm prin lista de studenți
        for student in student_json.get("students", []):
            loader.insert_student({"student": student})
            
    except Exception as e:
        print(f"A apărut o eroare la inserarea în baza de date: {e}")
    finally:
        # Folosim getattr pentru siguranță în cazul în care loader nu a fost inițializat corect
        if 'loader' in locals():
            loader.close()