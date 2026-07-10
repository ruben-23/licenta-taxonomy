// const mainContent = document.getElementById('main-content');
// const modal = document.getElementById('info-modal');
// const modalTitle = document.getElementById('modal-title');
// const modalBody = document.getElementById('modal-body');
//
// let currentCompanyPage = 0;
// let currentStudentPage = 0;
//
// // ==========================================
// // COMPANY WORKFLOW
// // ==========================================
//
// async function loadCompanyWorkflow(page = 0) {
//     currentCompanyPage = page;
//     mainContent.innerHTML = `<h3>Loading companies...</h3>`;
//
//     try {
//         const res = await fetch(`/api/companies?page=${page}&size=10`);
//         const companies = await res.json();
//
//         let html = `<h2>🏢 Company Directory</h2>`;
//
//         if (companies.length === 0 && page === 0) {
//             html += `<p>No companies found. Have you seeded the database?</p>`;
//         } else {
//             companies.forEach(company => {
//                 html += `
//                     <div class="card">
//                         <h4 onclick="showModal('company', '${company.company_id}', '${company.name}')">${company.name}</h4>
//                         <p><strong>Industry:</strong> ${company.industry || 'Not specified'}</p>
//                         <button class="btn-action" onclick="loadJobsForCompany('${company.company_id}', '${company.name}')">View Posted Jobs</button>
//                     </div>`;
//             });
//         }
//
//         // Pagination Controls
//         html += `
//             <div class="pagination">
//                 <button class="btn-secondary" onclick="loadCompanyWorkflow(${Math.max(0, page - 1)})" ${page === 0 ? 'disabled' : ''}>&larr; Previous</button>
//                 <span>Page ${page + 1}</span>
//                 <button class="btn-secondary" onclick="loadCompanyWorkflow(${page + 1})" ${companies.length < 10 ? 'disabled' : ''}>Next &rarr;</button>
//             </div>
//         `;
//         mainContent.innerHTML = html;
//     } catch (err) {
//         mainContent.innerHTML = `<h3 style="color:red;">Error loading companies: ${err.message}</h3>`;
//     }
// }
//
// async function loadJobsForCompany(companyId, companyName) {
//     mainContent.innerHTML = `<h3>Loading jobs for ${companyName}...</h3>`;
//     try {
//         const res = await fetch(`/api/companies/${companyId}/jobs`);
//         const jobs = await res.json();
//
//         let html = `
//             <button class="btn-secondary" onclick="loadCompanyWorkflow(currentCompanyPage)">&larr; Back to Companies</button>
//             <h2>Jobs at ${companyName}</h2>
//         `;
//
//         if (jobs.length === 0) {
//             html += `<p>No jobs posted by this company yet.</p>`;
//         } else {
//             jobs.forEach(job => {
//                 const jobId = job.job_id || job.id;
//                 html += `
//         <div class="card">
//             <h4 onclick="showModal('job', '${jobId}', '${job.title}')">${job.title}</h4>
//             <p>${job.description || 'No description provided.'}</p>
//             <div style="margin-top: 1rem; display: flex; gap: 0.5rem;">
//                 <button class="btn-action" style="background-color: #475569;" onclick="findCandidatesByEmbedding('${jobId}', '${job.title}')">Recommend</button>
//             </div>
//         </div>`;
//             });
//         }
//         mainContent.innerHTML = html;
//     } catch (err) {
//         mainContent.innerHTML = `<h3 style="color:red;">Error loading jobs: ${err.message}</h3>`;
//     }
// }
//
// // ==========================================
// // STUDENT WORKFLOW
// // ==========================================
//
// async function loadStudentWorkflow(page = 0) {
//     currentStudentPage = page;
//     mainContent.innerHTML = `<h3>Loading Students...</h3>`;
//     try {
//         const res = await fetch(`/api/students?page=${page}&size=10`);
//         const students = await res.json();
//         console.log('Students:', students);
//
//         let html = `<h2>🎓 Student Directory</h2>`;
//
//         if (students.length === 0 && page === 0) {
//             html += `<p>No students found in the database.</p>`;
//         } else {
//             students.forEach(student => {
//                 const studentId = student.studentId || student.id;
//                 html += `
//         <div class="card">
//             <h4 onclick="showModal('student', '${studentId}', '${student.name}')">${student.name}</h4>
//             <p>${student.bio || 'No bio available.'}</p>
//             <div style="margin-top: 1rem; display: flex; gap: 0.5rem;">
//                 <button class="btn-action" style="background-color: #475569;" onclick="findJobsByEmbedding('${studentId}', '${student.name}')">Recommend</button>
//             </div>
//         </div>`;
//             });
//         }
//
//         // Pagination Controls
//         html += `
//             <div class="pagination">
//                 <button class="btn-secondary" onclick="loadStudentWorkflow(${Math.max(0, page - 1)})" ${page === 0 ? 'disabled' : ''}>&larr; Previous</button>
//                 <span>Page ${page + 1}</span>
//                 <button class="btn-secondary" onclick="loadStudentWorkflow(${page + 1})" ${students.length < 10 ? 'disabled' : ''}>Next &rarr;</button>
//             </div>
//         `;
//         mainContent.innerHTML = html;
//     } catch (err) {
//         mainContent.innerHTML = `<h3 style="color:red;">Error loading students: ${err.message}</h3>`;
//     }
// }
//
// // ==========================================
// // MODAL & UI RENDERERS
// // ==========================================
//
// async function showModal(type, id, titleText) {
//     modalTitle.innerText = `Loading...`;
//     modalBody.innerHTML = `<p>Fetching details from database...</p>`;
//     modal.classList.remove('hidden');
//
//     const endpointMap = { 'company': 'companies', 'job': 'jobs', 'student': 'students' };
//     const apiRoute = endpointMap[type];
//
//     try {
//         const res = await fetch(`/api/${apiRoute}/${id}`);
//         if (!res.ok) throw new Error('Data not found');
//         const data = await res.json();
//
//         modalTitle.innerText = titleText || `${type.charAt(0).toUpperCase() + type.slice(1)} Details`;
//
//         let htmlContent = '';
//         switch(type) {
//             case 'company': htmlContent = renderCompany(data); break;
//             case 'job':     htmlContent = renderJob(data); break;
//             case 'student': htmlContent = renderStudent(data); break;
//             default:        htmlContent = `<p>Unknown entity type.</p>`;
//         }
//
//         modalBody.innerHTML = htmlContent;
//     } catch (err) {
//         modalTitle.innerText = `Error`;
//         modalBody.innerHTML = `<p style="color:red;">Could not load entity details: ${err.message}</p>`;
//     }
// }
//
// function renderCompany(company) {
//     let html = `<div class="details-container">`;
//     const displayId = company.company_id || company.id || 'N/A';
//     html += `<p><strong>Company ID:</strong> ${displayId}</p>`;
//     html += `<p><strong>Industry:</strong> ${company.industry || 'Not specified'}</p>`;
//     html += `<p><strong>Name:</strong> ${company.name || 'N/A'}</p>`;
//     html += `</div>`;
//     return html;
// }
//
// function renderJob(job) {
//     let html = `<div class="details-container">`;
//     const displayId = job.job_id || job.id || 'N/A';
//     html += `<p><strong>Job ID:</strong> ${displayId}</p>`;
//     html += `<p><strong>Job Title:</strong> ${job.title || 'N/A'}</p>`;
//     html += `<p><strong>Location:</strong> ${job.location || 'N/A'} ${job.remote ? '(Remote)' : ''}</p>`;
//     html += `<p><strong>Type:</strong> ${job.jobType || 'N/A'} - ${job.experienceLevel || 'N/A'}</p>`;
//     html += `<p><strong>Salary:</strong> ${job.salary ? job.salary + ' ' + (job.currency || '') : 'Not disclosed'}</p>`;
//     html += `<p><strong>Posted:</strong> ${job.postedDate || 'N/A'}</p>`;
//     html += `<p><strong>Expires:</strong> ${job.expiresAt || 'N/A'}</p>`;
//     html += `<h4 class="details-section-title">Description</h4>`;
//     html += `<p>${job.description || 'No description provided.'}</p>`;
//
//     if (job.requiredTechnologies && job.requiredTechnologies.length > 0) {
//         html += `<h4 class="details-section-title">Required Tech Stack</h4><div class="tags-wrapper">`;
//         job.requiredTechnologies.forEach(req => {
//             if (req.technology) {
//                 const techName = req.technology.name || 'Unknown';
//                 const importance = req.importance || 'Optional';
//                 let pillClass = importance.toLowerCase() === 'mandatory' ? 'mandatory' : (importance.toLowerCase() === 'nice-to-have' ? 'nice-to-have' : '');
//                 html += `<span class="tag-pill ${pillClass}">${techName} <small>(${importance})</small></span>`;
//             }
//         });
//         html += `</div>`;
//     }
//     html += `</div>`;
//     return html;
// }
//
// function renderStudent(student) {
//     let html = `<div class="details-container">`;
//     const displayId = student.studentId || student.id || 'N/A';
//     html += `<p><strong>Student ID:</strong> ${displayId}</p>`;
//     html += `<p><strong>Name:</strong> ${student.name || 'N/A'}</p>`;
//     html += `<h4 class="details-section-title">Biography</h4>`;
//     html += `<p>${student.bio || 'No bio available.'}</p>`;
//
//     if (student.technologies && student.technologies.length > 0) {
//         html += `<h4 class="details-section-title">Skills & Technologies</h4><div class="tags-wrapper">`;
//         student.technologies.forEach(tech => {
//             html += `<span class="tag-pill">${tech.name || tech}</span>`;
//         });
//         html += `</div>`;
//     }
//
//     if (student.courses && student.courses.length > 0) {
//         html += `<h4 class="details-section-title">Completed Courses</h4><ul class="details-list">`;
//         student.courses.forEach(course => {
//             html += `<li>${course.title || course.name || course.id}</li>`;
//         });
//         html += `</ul>`;
//     }
//     html += `</div>`;
//     return html;
// }
//
// // ==========================================
// // VECTOR / EMBEDDING MATCHING LOGIC
// // ==========================================
//
// async function findCandidatesByEmbedding(jobId, jobTitle) {
//     mainContent.innerHTML = `
//         <button class="btn-secondary" disabled>&larr; Back</button>
//         <h2>🔍 Running Vector Search for: ${jobTitle}...</h2>
//         <p><em>Calculating structural graph similarities...</em></p>
//     `;
//
//     try {
//         const res = await fetch(`/api/jobs/${jobId}/recommend-candidates/vector`);
//         const candidates = await res.json();
//
//         if (!res.ok) throw new Error(candidates.error || 'Backend failed');
//
//         let html = `
//             <button class="btn-secondary" onclick="loadCompanyWorkflow(currentCompanyPage)">&larr; Back to Companies</button>
//             <h2>🔍 Recommendations for: ${jobTitle}</h2>
//         `;
//
//         if (!candidates || candidates.length === 0) {
//             html += `<p>No vector matches found.</p>`;
//         } else {
//             candidates.forEach(student => {
//                 const score = student.similarityScore ? (student.similarityScore * 100).toFixed(1) : 0;
//                 html += `
//                     <div class="card">
//                         <h4 onclick="showModal('student', '${student.studentId}', 'Student Details')">${student.name}</h4>
//                         <span class="similarity-score">${score}% Match</span>
//                         <p style="margin-top: 0.5rem;"><strong>Academic:</strong> ${student.degreeLevel || ''} ${student.major || 'Unknown Major'} (Class of ${student.graduationYear || 'N/A'})</p>
//                 `;
//                 if (student.matchedTechnologies && student.matchedTechnologies.length > 0) {
//                     html += `<div class="tags-wrapper" style="margin-top: 0.5rem; margin-bottom: 0.5rem;"><strong>Matched:</strong> `;
//                     student.matchedTechnologies.forEach(tech => { html += `<span class="tag-pill nice-to-have">${tech}</span>`; });
//                     html += `</div>`;
//                 }
//                 if (student.missingTechnologies && student.missingTechnologies.length > 0) {
//                     html += `<div class="tags-wrapper" style="margin-bottom: 0.5rem;"><strong>Missing:</strong> `;
//                     student.missingTechnologies.forEach(tech => { html += `<span class="tag-pill missing">${tech}</span>`; });
//                     html += `</div>`;
//                 }
//                 html += `</div>`;
//             });
//         }
//         mainContent.innerHTML = html;
//     } catch (err) {
//         mainContent.innerHTML = `<h3 style="color:red;">Error: ${err.message}</h3><button class="btn-secondary" onclick="loadCompanyWorkflow(currentCompanyPage)">Go Back</button>`;
//     }
// }
//
// async function findJobsByEmbedding(studentId, studentName) {
//     mainContent.innerHTML = `
//         <button class="btn-secondary" disabled>&larr; Back</button>
//         <h2>🔍 Running Vector Search for: ${studentName}...</h2>
//         <p><em>Calculating structural graph similarities...</em></p>
//     `;
//
//     try {
//         // Note: Change '/vector' to '/graphsage' if your backend uses a different route!
//         const res = await fetch(`/api/students/${studentId}/recommend-jobs/vector`);
//         let jobs = await res.json();
//
//         console.log("Raw backend response for jobs:", jobs);
//
//         // If the backend wraps the array in a 'content' or 'matches' object, unwrap it
//         if (jobs && jobs.content) jobs = jobs.content;
//         else if (jobs && jobs.matches) jobs = jobs.matches;
//
//         if (!res.ok) throw new Error(jobs.error || 'Backend failed');
//
//         let html = `
//             <button class="btn-secondary" onclick="loadStudentWorkflow(currentStudentPage)">&larr; Back to Students</button>
//             <h2>🔍 Recommendations for: ${studentName}</h2>
//         `;
//
//         if (!jobs || jobs.length === 0) {
//             html += `<p>No vector matches found.</p>`;
//         } else {
//             jobs.forEach(job => {
//                 const score = job.similarityScore ? (job.similarityScore * 100).toFixed(1) : 0;
//                 html += `
//                     <div class="card">
//                         <h4 onclick="showModal('job', '${job.jobId}', 'Job Details')">${job.title}</h4>
//                         <span class="similarity-score">${score}% Match</span>
//                         <p style="margin-top: 0.5rem;"><strong>Company:</strong> ${job.companyName || 'Unknown'} | <strong>Location:</strong> ${job.location || 'N/A'} ${job.remote ? '(Remote)' : ''}</p>
//                 `;
//                 if (job.matchedTechnologies && job.matchedTechnologies.length > 0) {
//                     html += `<div class="tags-wrapper" style="margin-top: 0.5rem; margin-bottom: 0.5rem;"><strong>Matched Skills:</strong> `;
//                     job.matchedTechnologies.forEach(tech => { html += `<span class="tag-pill nice-to-have">${tech}</span>`; });
//                     html += `</div>`;
//                 }
//                 if (job.missingTechnologies && job.missingTechnologies.length > 0) {
//                     html += `<div class="tags-wrapper" style="margin-bottom: 0.5rem;"><strong>Missing Skills:</strong> `;
//                     job.missingTechnologies.forEach(tech => { html += `<span class="tag-pill missing">${tech}</span>`; });
//                     html += `</div>`;
//                 }
//                 html += `</div>`;
//             });
//         }
//         mainContent.innerHTML = html;
//     } catch (err) {
//         mainContent.innerHTML = `<h3 style="color:red;">Error: ${err.message}</h3><button class="btn-secondary" onclick="loadStudentWorkflow(currentStudentPage)">Go Back</button>`;
//     }
// }
//
// // ==========================================
// // DATA INGESTION
// // ==========================================
// async function ingestJobs() {
//     const contentArea = document.getElementById('main-content');
//     const ingestBtn = document.getElementById('ingest-btn');
//     if (!contentArea) { console.error("Could not find the main-content div!"); return; }
//
//     const originalText = ingestBtn ? ingestBtn.innerText : "Ingest Jobs";
//     if (ingestBtn) {
//         ingestBtn.disabled = true;
//         ingestBtn.innerText = "Ingesting...";
//     }
//
//     contentArea.innerHTML = `
//         <h2>📥 Job Ingestion in Progress</h2>
//         <p>The ETL pipeline is running. Please wait...</p>
//         <div class="loader"></div>
//     `;
//
//     try {
//         const res = await fetch('/api/ingestion', { method: 'GET' });
//         if (!res.ok) throw new Error(`Server returned ${res.status}`);
//         contentArea.innerHTML = `
//             <div class="card" style="border-left: 5px solid #059669;">
//                 <h2 style="color: #059669;">✅ Ingestion Complete</h2>
//                 <p>The pipeline has finished executing successfully.</p>
//                 <button class="btn-action" onclick="loadCompanyWorkflow(0)">View Companies</button>
//             </div>
//         `;
//     } catch (err) {
//         contentArea.innerHTML = `
//             <div class="card" style="border-left: 5px solid #dc2626;">
//                 <h2 style="color: #dc2626;">❌ Ingestion Failed</h2>
//                 <p><strong>Error:</strong> ${err.message}</p>
//                 <button class="btn-secondary" onclick="ingestJobs()">Try Again</button>
//             </div>
//         `;
//     } finally {
//         if (ingestBtn) {
//             ingestBtn.disabled = false;
//             ingestBtn.innerText = originalText;
//         }
//     }
// }
//
// function closeModal() {
//     modal.classList.add('hidden');
// }
//
// window.onclick = function(event) {
//     if (event.target == modal) {
//         closeModal();
//     }
// }



const mainContent = document.getElementById('main-content');
const modal = document.getElementById('info-modal');
const modalTitle = document.getElementById('modal-title');
const modalBody = document.getElementById('modal-body');

let currentCompanyPage = 0;
let currentStudentPage = 0;

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

        if (companies.length === 0 && page === 0) {
            html += `<p>No companies found. Have you seeded the database?</p>`;
        } else {
            companies.forEach(company => {
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
                <button class="btn-secondary" onclick="loadCompanyWorkflow(${page + 1})" ${companies.length < 10 ? 'disabled' : ''}>Next &rarr;</button>
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
                <button class="btn-action" style="background-color: #475569;" onclick="findCandidatesByEmbedding('${jobId}', '${job.title}')">Recommend</button>
            </div>
        </div>`;
            });
        }
        mainContent.innerHTML = html;
    } catch (err) {
        mainContent.innerHTML = `<h3 style="color:red;">Error loading jobs: ${err.message}</h3>`;
    }
}

// ==========================================
// STUDENT WORKFLOW
// ==========================================

async function loadStudentWorkflow(page = 0) {
    currentStudentPage = page;
    mainContent.innerHTML = `<h3>Loading Students...</h3>`;
    try {
        const res = await fetch(`/api/students?page=${page}&size=10`);
        const students = await res.json();
        console.log('Students:', students);

        let html = `<h2>🎓 Student Directory</h2>`;

        if (students.length === 0 && page === 0) {
            html += `<p>No students found in the database.</p>`;
        } else {
            students.forEach(student => {
                const studentId = student.studentId || student.id;
                html += `
        <div class="card">
            <h4 onclick="showModal('student', '${studentId}', '${student.name}')">${student.name}</h4>
            <p>${student.bio || 'No bio available.'}</p>
            <div style="margin-top: 1rem; display: flex; gap: 0.5rem;">
                <button class="btn-action" style="background-color: #475569;" onclick="findJobsByEmbedding('${studentId}', '${student.name}')">Recommend</button>
            </div>
        </div>`;
            });
        }

        // Pagination Controls
        html += `
            <div class="pagination">
                <button class="btn-secondary" onclick="loadStudentWorkflow(${Math.max(0, page - 1)})" ${page === 0 ? 'disabled' : ''}>&larr; Previous</button>
                <span>Page ${page + 1}</span>
                <button class="btn-secondary" onclick="loadStudentWorkflow(${page + 1})" ${students.length < 10 ? 'disabled' : ''}>Next &rarr;</button>
            </div>
        `;
        mainContent.innerHTML = html;
    } catch (err) {
        mainContent.innerHTML = `<h3 style="color:red;">Error loading students: ${err.message}</h3>`;
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

function renderCompany(company) {
    let html = `<div class="details-container">`;
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

    if (job.requiredTechnologies && job.requiredTechnologies.length > 0) {
        html += `<h4 class="details-section-title">Required Tech Stack</h4><div class="tags-wrapper">`;
        job.requiredTechnologies.forEach(req => {
            if (req.technology) {
                const techName = req.technology.name || 'Unknown';
                const importance = req.importance || 'Optional';
                let pillClass = importance.toLowerCase() === 'mandatory' ? 'mandatory' : (importance.toLowerCase() === 'nice-to-have' ? 'nice-to-have' : '');
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
    const displayId = student.studentId || student.id || 'N/A';
    html += `<p><strong>Student ID:</strong> ${displayId}</p>`;
    html += `<p><strong>Name:</strong> ${student.name || 'N/A'}</p>`;
    html += `<h4 class="details-section-title">Biography</h4>`;
    html += `<p>${student.bio || 'No bio available.'}</p>`;

    if (student.technologies && student.technologies.length > 0) {
        html += `<h4 class="details-section-title">Skills & Technologies</h4><div class="tags-wrapper">`;
        student.technologies.forEach(tech => {
            html += `<span class="tag-pill">${tech.name || tech}</span>`;
        });
        html += `</div>`;
    }

    if (student.courses && student.courses.length > 0) {
        html += `<h4 class="details-section-title">Completed Courses</h4><ul class="details-list">`;
        student.courses.forEach(course => {
            html += `<li>${course.title || course.name || course.id}</li>`;
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
            <h2>🔍 Recommendations for: ${jobTitle}</h2>
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
                if (student.matchedTechnologies && student.matchedTechnologies.length > 0) {
                    html += `<div class="tags-wrapper" style="margin-top: 0.5rem; margin-bottom: 0.5rem;"><strong>Matched:</strong> `;
                    student.matchedTechnologies.forEach(tech => { html += `<span class="tag-pill nice-to-have">${tech}</span>`; });
                    html += `</div>`;
                }
                if (student.missingTechnologies && student.missingTechnologies.length > 0) {
                    html += `<div class="tags-wrapper" style="margin-bottom: 0.5rem;"><strong>Missing:</strong> `;
                    student.missingTechnologies.forEach(tech => { html += `<span class="tag-pill missing">${tech}</span>`; });
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
        // Note: Change '/vector' to '/graphsage' if your backend uses a different route!
        const res = await fetch(`/api/students/${studentId}/recommend-jobs/vector`);
        let jobs = await res.json();

        console.log("Raw backend response for jobs:", jobs);

        // If the backend wraps the array in a 'content' or 'matches' object, unwrap it
        if (jobs && jobs.content) jobs = jobs.content;
        else if (jobs && jobs.matches) jobs = jobs.matches;

        if (!res.ok) throw new Error(jobs.error || 'Backend failed');

        let html = `
            <button class="btn-secondary" onclick="loadStudentWorkflow(currentStudentPage)">&larr; Back to Students</button>
            <h2>🔍 Recommendations for: ${studentName}</h2>
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
                if (job.matchedTechnologies && job.matchedTechnologies.length > 0) {
                    html += `<div class="tags-wrapper" style="margin-top: 0.5rem; margin-bottom: 0.5rem;"><strong>Matched Skills:</strong> `;
                    job.matchedTechnologies.forEach(tech => { html += `<span class="tag-pill nice-to-have">${tech}</span>`; });
                    html += `</div>`;
                }
                if (job.missingTechnologies && job.missingTechnologies.length > 0) {
                    html += `<div class="tags-wrapper" style="margin-bottom: 0.5rem;"><strong>Missing Skills:</strong> `;
                    job.missingTechnologies.forEach(tech => { html += `<span class="tag-pill missing">${tech}</span>`; });
                    html += `</div>`;
                }
                html += `</div>`;
            });
        }
        mainContent.innerHTML = html;
    } catch (err) {
        mainContent.innerHTML = `<h3 style="color:red;">Error: ${err.message}</h3><button class="btn-secondary" onclick="loadStudentWorkflow(currentStudentPage)">Go Back</button>`;
    }
}

// ==========================================
// DATA INGESTION
// ==========================================
async function ingestJobs() {
    const contentArea = document.getElementById('main-content');
    const ingestBtn = document.getElementById('ingest-btn');
    if (!contentArea) { console.error("Could not find the main-content div!"); return; }

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

// ==========================================
// NEW STUDENT / NEW JOB FORMS (HARDCODED PAYLOADS)
// ==========================================
// These payloads are what actually get POSTed when the "Add Student" /
// "Add Job" buttons are clicked. The on-screen form is pre-filled from the
// same object purely for display purposes — edit the objects below to
// change what gets submitted.

const NEW_STUDENT_PAYLOAD = {
    "student_id": "S-102938475",
    "name": "Elena Popescu",
    "major": "Data Science",
    "graduation_year": 2025,
    "current_year_of_study": 3,
    "degree_level": "Bachelor of Science",
    "knownTechnologies": [
        {
            "id": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
            "proficiency_level": 5,
            "years_of_experience": 3.0,
            "technology": { "skill_id": "SK-010", "name": "Python" }
        },
        {
            "id": "f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c",
            "proficiency_level": 4,
            "years_of_experience": 2.0,
            "technology": { "skill_id": "SK-011", "name": "Pandas" }
        },
        {
            "id": "1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5d6",
            "proficiency_level": 3,
            "years_of_experience": 1.5,
            "technology": { "skill_id": "SK-012", "name": "TensorFlow" }
        }
    ],
    "projects": [
        {
            "project_id": "PRJ-102",
            "title": "Predictive Retail Analytics Model",
            "description": "Developed a machine learning pipeline to forecast weekly retail store sales based on historical data, holiday markdown events, and regional economic indicators. The raw data was ingested and heavily cleaned using Python and Pandas to handle missing values and perform feature engineering. A deep neural network was then constructed and trained using TensorFlow, ultimately achieving a Mean Absolute Error 15% lower than the baseline linear regression model.",
            "github_link": "https://github.com/elenapopescu/retail-prediction-tf",
            "builtWith": [
                { "skill_id": "SK-010", "name": "Python" },
                { "skill_id": "SK-011", "name": "Pandas" },
                { "skill_id": "SK-012", "name": "TensorFlow" }
            ]
        }
    ],
    "courses": [
        {
            "course_id": "DS-405",
            "title": "Machine Learning Fundamentals",
            "description": "An intensive dive into supervised and unsupervised learning techniques, covering everything from linear regression and decision trees to clustering algorithms and dimensionality reduction. The course heavily utilized Python for weekly lab assignments, requiring students to build models from scratch before transitioning to industry-standard libraries like Scikit-learn and TensorFlow. Students were evaluated on their ability to apply Analytical thinking to interpret model outputs and tune hyperparameters to avoid overfitting.",
            "provider": "University Data Science Department",
            "covers": [
                { "skill_id": "SK-015", "name": "Machine Learning" },
                { "skill_id": "SK-016", "name": "Analytical thinking" },
                { "skill_id": "SK-010", "name": "Python" }
            ]
        }
    ],
    "diplomas": [
        {
            "diploma_id": "DIP-124",
            "title": "Google Data Analytics Professional Certificate",
            "description": "A comprehensive professional certificate covering the entire data analysis process, including data cleaning, analysis, and visualization. The program provided extensive hands-on experience querying large datasets with SQL and performing data manipulation using R and Pandas. It formally certifies the ability to transform raw data into actionable business insights through rigorous Data Visualization and analysis techniques.",
            "issuer": "Coursera & Google",
            "certifies": [
                { "skill_id": "SK-014", "name": "Data Visualization" },
                { "skill_id": "SK-013", "name": "SQL" },
                { "skill_id": "SK-011", "name": "Pandas" }
            ]
        }
    ]
};

const NEW_JOB_PAYLOAD = {
    "job_id": "aC7z-5sy5Sr6Y-LCAAAAAA==",
    "employer_name": "Tri-Force Consulting Services, Inc.",
    "employer_website": null,
    "job_title": "Software Developer/Engineer",
    "job_description": "Job Description\nJob Description\n\nName of position: Software Developer/Engineer\n\nLocation: Philadelphia PA\n\nclient: Philadelphia Gas Works\n\nnote: its on site position by day one\n\njob description\n\n· Minimum seven to nine years of hands on experience in designing, developing and supporting multiple mission critical web and windows applications using application/data security best practices and following programming languages/technologies is a MUST.\n\n· ASP.NET (4.7+) with MVC and/or Razor pages, C#, .Net Core 3+ and/or .Net 6 Programming\n\n· VB.Net and MSMQ\n\n· HTML5, CSS, TypeScript, Bootstrap, Angular JS/6 +\n\n· .Net Core 3+, .Net 6\n\n· RESTful APIs and SOAP Services\n\n· Oracle SQL and PL/SQL programming and batch processing\n\n· Customer focus, creative problem solving, and interpersonal skills is a MUST\n\n· Experience working with custom software solutions and customer programs for utilities industry is a plus.\n\n· Experience with python programming is a plus.\n\n· Experience with Production Control/support process and best practices and enterprise job scheduling tools like UC4 Automic is a plus.\n\n· Experience interfacing with enterprise application systems like Oracle C2M and Oracle Financial, ESRI/GIS as well as upgrading applications to .Net 6 and above is a plus\n\nIf you are: bright, motivated, skilled, a difference-maker, able to get things done, work with minimum direction, enthusiastic, a thinker, able to juggle and multi-task, communicate effectively, and lead, then we would like to hear from you. We need exceptionally capable people for this role for our client, so get back to us and tell us why you think you are a fit.\n\nAbout Us:\n\nTri-Force is one of the fastest growing companies in Philadelphia region by receiving the award 5 times and 3 times(Ranked #931 2021) on Inc. 5000 fastest growing companies in the USA. Tri-Force Consulting Services, Inc. is an established consulting services firm offering innovative solutions to Government and Commercial sectors. We specialize in building customized software applications solutions such as knowledge management systems, business intelligence, data analysis, database support and maintenance, data warehouse implementation and support, systems architecture and systems integration for our clients. Our technical competencies are in Java, .NET, SharePoint, PHP, Business Intelligence (Cognos, Data Warehouse), mobile applications platforms (iPhone, iPad, Android, Blackberry), and various other technologies. We also specialize in providing resources to manage infrastructure projects. Tri-Force is one of the fastest growing companies in Philadelphia region by receiving the award 5 times and 2 times on Inc. 5000 fastest growing companies in the USA.",
    "job_employment_type": null,
    "job_city": "False Pass",
    "job_country": "US",
    "job_is_remote": false,
    "job_posted_at_datetime_utc": "2026-05-15T12:04:25Z",
    "job_offer_expiration_datetime_utc": null,
    "job_min_salary": 100000,
    "job_max_salary": 120000,
    "job_salary_currency": null,
    "job_required_experience": null
};

function loadNewStudentForm() {
    mainContent.innerHTML = renderNewStudentForm();
}

function renderNewStudentForm() {
    const s = NEW_STUDENT_PAYLOAD;

    let techHtml = '';
    s.knownTechnologies.forEach((kt, i) => {
        techHtml += `
            <div class="sub-card">
                <div class="form-grid">
                    <div class="form-group"><label>Skill ID</label><input type="text" readonly value="${kt.technology.skill_id}"></div>
                    <div class="form-group"><label>Technology Name</label><input type="text" readonly value="${kt.technology.name}"></div>
                    <div class="form-group"><label>Proficiency Level (1-5)</label><input type="number" readonly value="${kt.proficiency_level}"></div>
                    <div class="form-group"><label>Years of Experience</label><input type="number" readonly value="${kt.years_of_experience}"></div>
                </div>
            </div>`;
    });

    let projectsHtml = '';
    s.projects.forEach((p) => {
        projectsHtml += `
            <div class="sub-card">
                <div class="form-grid">
                    <div class="form-group"><label>Project ID</label><input type="text" readonly value="${p.project_id}"></div>
                    <div class="form-group"><label>Title</label><input type="text" readonly value="${p.title}"></div>
                    <div class="form-group full-width"><label>GitHub Link</label><input type="text" readonly value="${p.github_link || ''}"></div>
                    <div class="form-group full-width"><label>Description</label><textarea readonly>${p.description}</textarea></div>
                    <div class="form-group full-width"><label>Built With</label><input type="text" readonly value="${p.builtWith.map(t => t.name).join(', ')}"></div>
                </div>
            </div>`;
    });

    let coursesHtml = '';
    s.courses.forEach((c) => {
        coursesHtml += `
            <div class="sub-card">
                <div class="form-grid">
                    <div class="form-group"><label>Course ID</label><input type="text" readonly value="${c.course_id}"></div>
                    <div class="form-group"><label>Title</label><input type="text" readonly value="${c.title}"></div>
                    <div class="form-group full-width"><label>Provider</label><input type="text" readonly value="${c.provider}"></div>
                    <div class="form-group full-width"><label>Description</label><textarea readonly>${c.description}</textarea></div>
                    <div class="form-group full-width"><label>Covers</label><input type="text" readonly value="${c.covers.map(t => t.name).join(', ')}"></div>
                </div>
            </div>`;
    });

    let diplomasHtml = '';
    s.diplomas.forEach((d) => {
        diplomasHtml += `
            <div class="sub-card">
                <div class="form-grid">
                    <div class="form-group"><label>Diploma ID</label><input type="text" readonly value="${d.diploma_id}"></div>
                    <div class="form-group"><label>Title</label><input type="text" readonly value="${d.title}"></div>
                    <div class="form-group full-width"><label>Issuer</label><input type="text" readonly value="${d.issuer}"></div>
                    <div class="form-group full-width"><label>Description</label><textarea readonly>${d.description}</textarea></div>
                    <div class="form-group full-width"><label>Certifies</label><input type="text" readonly value="${d.certifies.map(t => t.name).join(', ')}"></div>
                </div>
            </div>`;
    });

    return `
        <div class="form-header">
            <h2>🎓 Add New Student</h2>
            <button class="btn-action" id="add-student-btn" onclick="submitNewStudent()">Add Student</button>
        </div>
        
        <div id="new-student-status"></div>

        <div class="card">
            <h4 class="form-section-heading">Basic Information</h4>
            <div class="form-grid">
                <div class="form-group"><label>Student ID</label><input type="text" readonly value="${s.student_id}"></div>
                <div class="form-group"><label>Name</label><input type="text" readonly value="${s.name}"></div>
                <div class="form-group"><label>Major</label><input type="text" readonly value="${s.major}"></div>
                <div class="form-group"><label>Degree Level</label><input type="text" readonly value="${s.degree_level}"></div>
                <div class="form-group"><label>Graduation Year</label><input type="number" readonly value="${s.graduation_year}"></div>
                <div class="form-group"><label>Current Year of Study</label><input type="number" readonly value="${s.current_year_of_study}"></div>
            </div>
        </div>

        <div class="card">
            <h4 class="form-section-heading">Known Technologies</h4>
            ${techHtml}
        </div>

        <div class="card">
            <h4 class="form-section-heading">Projects</h4>
            ${projectsHtml}
        </div>

        <div class="card">
            <h4 class="form-section-heading">Courses</h4>
            ${coursesHtml}
        </div>

        <div class="card">
            <h4 class="form-section-heading">Diplomas</h4>
            ${diplomasHtml}
        </div>
    `;
}

async function submitNewStudent() {
    const btn = document.getElementById('add-student-btn');
    const statusDiv = document.getElementById('new-student-status');
    const originalText = btn ? btn.innerText : 'Add Student';

    if (btn) { btn.disabled = true; btn.innerText = 'Adding...'; }
    if (statusDiv) statusDiv.innerHTML = '';

    try {
        const res = await fetch('/api/ingest/student', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(NEW_STUDENT_PAYLOAD)
        });
        if (!res.ok) throw new Error(`Server returned ${res.status}`);
        if (statusDiv) {
            statusDiv.innerHTML = `
                <div class="card form-status-success">
                    <p>✅ Student "${NEW_STUDENT_PAYLOAD.name}" was added successfully.</p>
                </div>`;
        }
    } catch (err) {
        if (statusDiv) {
            statusDiv.innerHTML = `
                <div class="card form-status-error">
                    <p>❌ Failed to add student: ${err.message}</p>
                </div>`;
        }
    } finally {
        if (btn) { btn.disabled = false; btn.innerText = originalText; }
    }
}

function loadNewJobForm() {
    mainContent.innerHTML = renderNewJobForm();
}

function renderNewJobForm() {
    const j = NEW_JOB_PAYLOAD;

    return `
        <div class="form-header">
            <h2>💼 Add New Job</h2>
            <button class="btn-action" id="add-job-btn" onclick="submitNewJob()">Add Job</button>
        </div>
        
        <div id="new-job-status"></div>

        <div class="card">
            <h4 class="form-section-heading">Job Details</h4>
            <div class="form-grid">
                <div class="form-group"><label>Job ID</label><input type="text" readonly value="${j.job_id}"></div>
                <div class="form-group"><label>Job Title</label><input type="text" readonly value="${j.job_title}"></div>
                <div class="form-group"><label>Employer Name</label><input type="text" readonly value="${j.employer_name}"></div>
                <div class="form-group"><label>Employer Website</label><input type="text" readonly value="${j.employer_website || ''}"></div>
                <div class="form-group"><label>Employment Type</label><input type="text" readonly value="${j.job_employment_type || ''}"></div>
                <div class="form-group"><label>Required Experience</label><input type="text" readonly value="${j.job_required_experience || ''}"></div>
                <div class="form-group"><label>City</label><input type="text" readonly value="${j.job_city || ''}"></div>
                <div class="form-group"><label>Country</label><input type="text" readonly value="${j.job_country || ''}"></div>
                <div class="form-group">
                    <label>Remote</label>
                    <input type="checkbox" readonly disabled ${j.job_is_remote ? 'checked' : ''}>
                </div>
                <div class="form-group"><label>Posted At (UTC)</label><input type="text" readonly value="${j.job_posted_at_datetime_utc || ''}"></div>
                <div class="form-group"><label>Expires At (UTC)</label><input type="text" readonly value="${j.job_offer_expiration_datetime_utc || ''}"></div>
                <div class="form-group"><label>Min Salary</label><input type="number" readonly value="${j.job_min_salary ?? ''}"></div>
                <div class="form-group"><label>Max Salary</label><input type="number" readonly value="${j.job_max_salary ?? ''}"></div>
                <div class="form-group"><label>Salary Currency</label><input type="text" readonly value="${j.job_salary_currency || ''}"></div>
                <div class="form-group full-width"><label>Description</label><textarea readonly style="min-height: 220px;">${j.job_description}</textarea></div>
            </div>
        </div>
    `;
}

async function submitNewJob() {
    const btn = document.getElementById('add-job-btn');
    const statusDiv = document.getElementById('new-job-status');
    const originalText = btn ? btn.innerText : 'Add Job';

    if (btn) { btn.disabled = true; btn.innerText = 'Adding...'; }
    if (statusDiv) statusDiv.innerHTML = '';

    try {
        const res = await fetch('/api/ingest/job', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(NEW_JOB_PAYLOAD)
        });
        if (!res.ok) throw new Error(`Server returned ${res.status}`);
        if (statusDiv) {
            statusDiv.innerHTML = `
                <div class="card form-status-success">
                    <p>✅ Job "${NEW_JOB_PAYLOAD.job_title}" was added successfully.</p>
                </div>`;
        }
    } catch (err) {
        if (statusDiv) {
            statusDiv.innerHTML = `
                <div class="card form-status-error">
                    <p>❌ Failed to add job: ${err.message}</p>
                </div>`;
        }
    } finally {
        if (btn) { btn.disabled = false; btn.innerText = originalText; }
    }
}

function closeModal() {
    modal.classList.add('hidden');
}

window.onclick = function(event) {
    if (event.target == modal) {
        closeModal();
    }
}
