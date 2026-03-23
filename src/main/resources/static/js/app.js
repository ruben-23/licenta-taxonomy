const mainContent = document.getElementById('main-content');
const modal = document.getElementById('info-modal');
const modalTitle = document.getElementById('modal-title');
const modalBody = document.getElementById('modal-body');

let currentCompanyPage = 0;

// ==========================================
// COMPANY WORKFLOW
// ==========================================

async function loadCompanyWorkflow(page = 0) {
    currentCompanyPage = page;
    mainContent.innerHTML = `<h3>Loading companies...</h3>`;

    try {
        const res = await fetch(`/api/companies?page=${page}&size=10`);
        const companies = await res.json();

        let html = `<h2>🏢 Company Directory</h2>`;

        if (companies.length === 0) {
            html += `<p>No companies found. Have you seeded the database?</p>`;
        } else {
            companies.forEach(company => {
                console.log("COMPANY ID: " + company.company_id);
                html += `
                    <div class="card">
                        <h4 onclick="showModal('company', '${company.company_id}', '${company.name}')">${company.name}</h4>
                        <p><strong>Industry:</strong> ${company.industry || 'Not specified'}</p>
                        <button class="btn-action" onclick="loadJobsForCompany('${company.company_id}', '${company.name}')">View Posted Jobs</button>
                    </div>`;
            });
        }

        // Pagination Controls
        html += `
            <div class="pagination">
                <button class="btn-secondary" onclick="loadCompanyWorkflow(${Math.max(0, page - 1)})" ${page === 0 ? 'disabled' : ''}>&larr; Previous</button>
                <span>Page ${page + 1}</span>
                <button class="btn-secondary" onclick="loadCompanyWorkflow(${page + 1})">Next &rarr;</button>
            </div>
        `;
        mainContent.innerHTML = html;
    } catch (err) {
        mainContent.innerHTML = `<h3 style="color:red;">Error loading companies: ${err.message}</h3>`;
    }
}

async function loadJobsForCompany(companyId, companyName) {
    mainContent.innerHTML = `<h3>Loading jobs for ${companyName}...</h3>`;
    try {
        const res = await fetch(`/api/companies/${companyId}/jobs`);
        const jobs = await res.json();

        let html = `
            <button class="btn-secondary" onclick="loadCompanyWorkflow(currentCompanyPage)">&larr; Back to Companies</button>
            <h2>Jobs at ${companyName}</h2>
        `;

        if (jobs.length === 0) {
            html += `<p>No jobs posted by this company yet.</p>`;
        } else {
            jobs.forEach(job => {
                const jobId = job.job_id || job.id;
                html += `
        <div class="card">
            <h4 onclick="showModal('job', '${jobId}', '${job.title}')">${job.title}</h4>
            <p>${job.description || 'No description provided.'}</p>
            <div style="margin-top: 1rem; display: flex; gap: 0.5rem;">
                <button class="btn-action" onclick="findCandidates('${jobId}', '${job.title}')">✨ AI Match</button>
                <button class="btn-action" style="background-color: #475569;" onclick="findCandidatesByEmbedding('${jobId}', '${job.title}')">🔍 Embedding Match</button>
            </div>
        </div>`;
            });
        }
        mainContent.innerHTML = html;
    } catch (err) {
        mainContent.innerHTML = `<h3 style="color:red;">Error loading jobs: ${err.message}</h3>`;
    }
}

async function findCandidates(jobId, jobTitle) {
    mainContent.innerHTML = `
        <button class="btn-secondary" disabled>&larr; Back</button>
        <h2>Analyzing candidates for: ${jobTitle}...</h2>
        <p><em>Gemini is writing a recommendation report. This may take a few seconds.</em></p>
    `;

    try {
        const res = await fetch(`/api/jobs/${jobId}/recommend-candidates`);
        const data = await res.json();

        // Parse the Markdown string into HTML
        const parsedHtml = marked.parse(data.markdown);

        mainContent.innerHTML = `
            <button class="btn-secondary" onclick="loadCompanyWorkflow(currentCompanyPage)">&larr; Start Over</button>
            <div class="card markdown-container" style="margin-top: 1rem;">
                ${parsedHtml}
            </div>
        `;
    } catch (err) {
        mainContent.innerHTML = `<h3 style="color:red;">Error: ${err.message}</h3><button class="btn-secondary" onclick="loadCompanyWorkflow(currentCompanyPage)">Go Back</button>`;
    }
}

// ==========================================
// STUDENT WORKFLOW
// ==========================================

async function loadStudentWorkflow() {
    mainContent.innerHTML = `<h3>Loading Students...</h3>`;
    try {
        const res = await fetch(`/api/students`);
        const students = await res.json();

        let html = `<h2>🎓 Student Directory</h2>`;

        if (students.length === 0) {
            html += `<p>No students found in the database.</p>`;
        } else {
            students.forEach(student => {
                const studentId = student.student_id || student.id;
                html += `
        <div class="card">
            <h4 onclick="showModal('student', '${studentId}', '${student.name}')">${student.name}</h4>
            <p>${student.bio || 'No bio available.'}</p>
            <div style="margin-top: 1rem; display: flex; gap: 0.5rem;">
                <button class="btn-action" onclick="findJobsForStudent('${studentId}', '${student.name}')">✨ AI Match</button>
                <button class="btn-action" style="background-color: #475569;" onclick="findJobsByEmbedding('${studentId}', '${student.name}')">🔍 Embedding Match</button>
            </div>
        </div>`;
            });
        }
        mainContent.innerHTML = html;
    } catch (err) {
        mainContent.innerHTML = `<h3 style="color:red;">Error loading students: ${err.message}</h3>`;
    }
}

async function findJobsForStudent(studentId, studentName) {
    mainContent.innerHTML = `
        <button class="btn-secondary" disabled>&larr; Back</button>
        <h2>Finding best jobs for: ${studentName}...</h2>
        <p><em>Gemini is writing a career recommendation report...</em></p>
    `;

    try {
        const res = await fetch(`/api/students/${studentId}/recommend-jobs`);
        const data = await res.json();

        // Parse the Markdown string into HTML
        const parsedHtml = marked.parse(data.markdown);

        mainContent.innerHTML = `
            <button class="btn-secondary" onclick="loadStudentWorkflow()">&larr; Back to Students</button>
            <div class="card markdown-container" style="margin-top: 1rem;">
                ${parsedHtml}
            </div>
        `;
    } catch (err) {
        mainContent.innerHTML = `<h3 style="color:red;">Error: ${err.message}</h3><button class="btn-secondary" onclick="loadStudentWorkflow()">Go Back</button>`;
    }
}

// ==========================================
// MODAL & UI RENDERERS
// ==========================================

async function showModal(type, id, titleText) {
    modalTitle.innerText = `Loading...`;
    modalBody.innerHTML = `<p>Fetching details from database...</p>`;
    modal.classList.remove('hidden');

    const endpointMap = { 'company': 'companies', 'job': 'jobs', 'student': 'students' };
    const apiRoute = endpointMap[type];

    try {
        const res = await fetch(`/api/${apiRoute}/${id}`);
        if (!res.ok) throw new Error('Data not found');
        const data = await res.json();

        modalTitle.innerText = titleText || `${type.charAt(0).toUpperCase() + type.slice(1)} Details`;

        // Route to the correct HTML generator
        let htmlContent = '';
        switch(type) {
            case 'company': htmlContent = renderCompany(data); break;
            case 'job':     htmlContent = renderJob(data); break;
            case 'student': htmlContent = renderStudent(data); break;
            default:        htmlContent = `<p>Unknown entity type.</p>`;
        }

        modalBody.innerHTML = htmlContent;
    } catch (err) {
        modalTitle.innerText = `Error`;
        modalBody.innerHTML = `<p style="color:red;">Could not load entity details: ${err.message}</p>`;
    }
}

// --- Specific Entity Formatters ---
function renderCompany(company) {
    let html = `<div class="details-container">`;
    // Look for company_id first, fallback to id
    const displayId = company.company_id || company.id || 'N/A';
    html += `<p><strong>Company ID:</strong> ${displayId}</p>`;
    html += `<p><strong>Industry:</strong> ${company.industry || 'Not specified'}</p>`;
    html += `<p><strong>Name:</strong> ${company.name || 'N/A'}</p>`;
    html += `</div>`;
    return html;
}
function renderJob(job) {
    let html = `<div class="details-container">`;
    const displayId = job.job_id || job.id || 'N/A';
    html += `<p><strong>Job ID:</strong> ${displayId}</p>`;
    html += `<p><strong>Job Title:</strong> ${job.title || 'N/A'}</p>`;
    html += `<p><strong>Location:</strong> ${job.location || 'N/A'} ${job.remote ? '(Remote)' : ''}</p>`;
    html += `<p><strong>Type:</strong> ${job.jobType || 'N/A'} - ${job.experienceLevel || 'N/A'}</p>`;
    html += `<p><strong>Salary:</strong> ${job.salary ? job.salary + ' ' + (job.currency || '') : 'Not disclosed'}</p>`;
    html += `<p><strong>Posted:</strong> ${job.postedDate || 'N/A'}</p>`;
    html += `<p><strong>Expires:</strong> ${job.expiresAt || 'N/A'}</p>`;

    html += `<h4 class="details-section-title">Description</h4>`;
    html += `<p>${job.description || 'No description provided.'}</p>`;

    // --- UPDATED: Parsing the nested relationship objects ---
    if (job.requiredTechnologies && job.requiredTechnologies.length > 0) {
        html += `<h4 class="details-section-title">Required Tech Stack</h4><div class="tags-wrapper">`;

        job.requiredTechnologies.forEach(req => {
            // Check if the nested 'technology' node exists inside the relationship
            if (req.technology) {
                const techName = req.technology.name || 'Unknown';
                const importance = req.importance || 'Optional';

                // Determine CSS class based on importance
                let pillClass = '';
                if (importance.toLowerCase() === 'mandatory') {
                    pillClass = 'mandatory';
                } else if (importance.toLowerCase() === 'nice-to-have') {
                    pillClass = 'nice-to-have';
                }

                html += `<span class="tag-pill ${pillClass}">${techName} <small>(${importance})</small></span>`;
            }
        });
        html += `</div>`;
    }

    html += `</div>`;
    return html;
}

function renderStudent(student) {
    let html = `<div class="details-container">`;
    // Look for student_id first, fallback to id
    const displayId = student.student_id || student.id || 'N/A';
    html += `<p><strong>Student ID:</strong> ${displayId}</p>`;
    html += `<p><strong>Name:</strong> ${student.name || 'N/A'}</p>`;

    html += `<h4 class="details-section-title">Biography</h4>`;
    html += `<p>${student.bio || 'No bio available.'}</p>`;

    if (student.technologies && student.technologies.length > 0) {
        html += `<h4 class="details-section-title">Skills & Technologies</h4><div class="tags-wrapper">`;
        student.technologies.forEach(tech => {
            const techName = tech.name || tech;
            html += `<span class="tag-pill">${techName}</span>`;
        });
        html += `</div>`;
    }

    if (student.courses && student.courses.length > 0) {
        html += `<h4 class="details-section-title">Completed Courses</h4><ul class="details-list">`;
        student.courses.forEach(course => {
            const courseName = course.title || course.name || course.id;
            html += `<li>${courseName}</li>`;
        });
        html += `</ul>`;
    }

    html += `</div>`;
    return html;
}


// ==========================================
// VECTOR / EMBEDDING MATCHING LOGIC
// ==========================================

async function findCandidatesByEmbedding(jobId, jobTitle) {
    mainContent.innerHTML = `
        <button class="btn-secondary" disabled>&larr; Back</button>
        <h2>🔍 Running Vector Search for: ${jobTitle}...</h2>
        <p><em>Calculating structural graph similarities...</em></p>
    `;

    try {
        const res = await fetch(`/api/jobs/${jobId}/recommend-candidates/vector`);
        const candidates = await res.json();

        if (!res.ok) throw new Error(candidates.error || 'Backend failed');

        let html = `
            <button class="btn-secondary" onclick="loadCompanyWorkflow(currentCompanyPage)">&larr; Back to Companies</button>
            <h2>🔍 Embedding Matches for: ${jobTitle}</h2>
        `;

        if (!candidates || candidates.length === 0) {
            html += `<p>No vector matches found.</p>`;
        } else {
            candidates.forEach(student => {
                const score = student.similarityScore ? (student.similarityScore * 100).toFixed(1) : 0;

                html += `
                    <div class="card">
                        <h4 onclick="showModal('student', '${student.studentId}', 'Student Details')">${student.name}</h4>
                        <span class="similarity-score">${score}% Match</span>
                        <p style="margin-top: 0.5rem;"><strong>Academic:</strong> ${student.degreeLevel || ''} ${student.major || 'Unknown Major'} (Class of ${student.graduationYear || 'N/A'})</p>
                `;

                // Render Matched Technologies
                if (student.matchedTechnologies && student.matchedTechnologies.length > 0) {
                    html += `<div class="tags-wrapper" style="margin-top: 0.5rem; margin-bottom: 0.5rem;"><strong>Matched:</strong> `;
                    student.matchedTechnologies.forEach(tech => {
                        html += `<span class="tag-pill nice-to-have">${tech}</span>`;
                    });
                    html += `</div>`;
                }

                // Render Missing Technologies
                if (student.missingTechnologies && student.missingTechnologies.length > 0) {
                    html += `<div class="tags-wrapper" style="margin-bottom: 0.5rem;"><strong>Missing:</strong> `;
                    student.missingTechnologies.forEach(tech => {
                        html += `<span class="tag-pill missing">${tech}</span>`;
                    });
                    html += `</div>`;
                }

                html += `</div>`;
            });
        }
        mainContent.innerHTML = html;
    } catch (err) {
        mainContent.innerHTML = `<h3 style="color:red;">Error: ${err.message}</h3><button class="btn-secondary" onclick="loadCompanyWorkflow(currentCompanyPage)">Go Back</button>`;
    }
}

async function findJobsByEmbedding(studentId, studentName) {
    mainContent.innerHTML = `
        <button class="btn-secondary" disabled>&larr; Back</button>
        <h2>🔍 Running Vector Search for: ${studentName}...</h2>
        <p><em>Calculating structural graph similarities...</em></p>
    `;

    try {
        const res = await fetch(`/api/students/${studentId}/recommend-jobs/vector`);
        const jobs = await res.json();

        if (!res.ok) throw new Error(jobs.error || 'Backend failed');

        let html = `
            <button class="btn-secondary" onclick="loadStudentWorkflow()">&larr; Back to Students</button>
            <h2>🔍 Embedding Matches for: ${studentName}</h2>
        `;

        if (!jobs || jobs.length === 0) {
            html += `<p>No vector matches found.</p>`;
        } else {
            jobs.forEach(job => {
                const score = job.similarityScore ? (job.similarityScore * 100).toFixed(1) : 0;

                html += `
                    <div class="card">
                        <h4 onclick="showModal('job', '${job.jobId}', 'Job Details')">${job.title}</h4>
                        <span class="similarity-score">${score}% Match</span>
                        <p style="margin-top: 0.5rem;"><strong>Company:</strong> ${job.companyName || 'Unknown'} | <strong>Location:</strong> ${job.location || 'N/A'} ${job.remote ? '(Remote)' : ''}</p>
                `;

                // Render Matched Technologies
                if (job.matchedTechnologies && job.matchedTechnologies.length > 0) {
                    html += `<div class="tags-wrapper" style="margin-top: 0.5rem; margin-bottom: 0.5rem;"><strong>Matched Skills:</strong> `;
                    job.matchedTechnologies.forEach(tech => {
                        html += `<span class="tag-pill nice-to-have">${tech}</span>`;
                    });
                    html += `</div>`;
                }

                // Render Missing Technologies
                if (job.missingTechnologies && job.missingTechnologies.length > 0) {
                    html += `<div class="tags-wrapper" style="margin-bottom: 0.5rem;"><strong>Missing Skills:</strong> `;
                    job.missingTechnologies.forEach(tech => {
                        html += `<span class="tag-pill missing">${tech}</span>`;
                    });
                    html += `</div>`;
                }

                html += `</div>`;
            });
        }
        mainContent.innerHTML = html;
    } catch (err) {
        mainContent.innerHTML = `<h3 style="color:red;">Error: ${err.message}</h3><button class="btn-secondary" onclick="loadStudentWorkflow()">Go Back</button>`;
    }
}


// ==========================================
// DATA INGESTION
// ==========================================
async function ingestJobs() {
    // Re-select to ensure we have the element in scope
    const contentArea = document.getElementById('main-content');
    const ingestBtn = document.getElementById('ingest-btn');

    // Safety check: if the element doesn't exist, stop the error
    if (!contentArea) {
        console.error("Could not find the main-content div!");
        return;
    }

    const originalText = ingestBtn ? ingestBtn.innerText : "Ingest Jobs";

    if (ingestBtn) {
        ingestBtn.disabled = true;
        ingestBtn.innerText = "Ingesting...";
    }

    contentArea.innerHTML = `
        <h2>📥 Job Ingestion in Progress</h2>
        <p>The ETL pipeline is running. Please wait...</p>
        <div class="loader"></div> 
    `;

    try {
        const res = await fetch('/api/ingestion', { method: 'GET' });

        if (!res.ok) throw new Error(`Server returned ${res.status}`);

        contentArea.innerHTML = `
            <div class="card" style="border-left: 5px solid #059669;">
                <h2 style="color: #059669;">✅ Ingestion Complete</h2>
                <p>The pipeline has finished executing successfully.</p>
                <button class="btn-action" onclick="loadCompanyWorkflow(0)">View Companies</button>
            </div>
        `;
    } catch (err) {
        contentArea.innerHTML = `
            <div class="card" style="border-left: 5px solid #dc2626;">
                <h2 style="color: #dc2626;">❌ Ingestion Failed</h2>
                <p><strong>Error:</strong> ${err.message}</p>
                <button class="btn-secondary" onclick="ingestJobs()">Try Again</button>
            </div>
        `;
    } finally {
        if (ingestBtn) {
            ingestBtn.disabled = false;
            ingestBtn.innerText = originalText;
        }
    }
}


function closeModal() {
    modal.classList.add('hidden');
}

// Close modal if user clicks outside the modal content box
window.onclick = function(event) {
    if (event.target == modal) {
        closeModal();
    }
}