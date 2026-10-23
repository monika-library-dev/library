/**
 * Library Book Issue Management System - Frontend Controller
 * Uses modern vanilla JavaScript with Async/Await and Fetch API.
 */

const API_BASE = '/api';

// Cache for dropdowns
let cachedBooks = [];
let cachedMembers = [];
let currentIssueFilter = 'ALL';

// --- Initialization ---
document.addEventListener('DOMContentLoaded', () => {
    setupTabNavigation();
    loadDashboard();
});

// --- Tab Navigation ---
function setupTabNavigation() {
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(btn => {
        btn.addEventListener('click', () => {
            const tab = btn.getAttribute('data-tab');
            switchTab(tab);
        });
    });
}

function switchTab(tabName) {
    // Update active nav button
    document.querySelectorAll('.nav-item').forEach(btn => {
        btn.classList.toggle('active', btn.getAttribute('data-tab') === tabName);
    });

    // Update active tab pane
    document.querySelectorAll('.tab-pane').forEach(pane => {
        pane.classList.remove('active');
    });
    const targetPane = document.getElementById(`tab-${tabName}`);
    if (targetPane) targetPane.classList.add('active');

    // Update header titles
    const titles = {
        'dashboard': ['Dashboard Overview', 'Real-time overview of library transactions and inventory'],
        'books': ['Book Catalog', 'Manage, search, and monitor book stock'],
        'members': ['Members Directory', 'Registered students and faculty members'],
        'issues': ['Issue & Return Desk', 'Track active borrowings, overdue books, and returns']
    };
    if (titles[tabName]) {
        document.getElementById('current-page-title').innerText = titles[tabName][0];
        document.getElementById('current-page-subtitle').innerText = titles[tabName][1];
    }

    // Refresh tab content
    if (tabName === 'dashboard') loadDashboard();
    if (tabName === 'books') loadBooks();
    if (tabName === 'members') loadMembers();
    if (tabName === 'issues') loadIssues(currentIssueFilter);
}

// --- Dashboard ---
async function loadDashboard() {
    try {
        const res = await fetch(`${API_BASE}/stats`);
        if (!res.ok) throw new Error('Failed to load stats');
        const stats = await res.json();

        document.getElementById('stat-total-books').innerText = stats.totalCopies ?? 0;
        document.getElementById('stat-unique-titles').innerText = `${stats.uniqueTitles ?? 0} unique titles`;
        document.getElementById('stat-available-books').innerText = stats.availableCopies ?? 0;
        document.getElementById('stat-active-issues').innerText = stats.activeIssues ?? 0;
        document.getElementById('stat-overdue-issues').innerText = stats.overdueIssues ?? 0;

        // Load recent issues for quick dashboard table
        loadRecentIssues();
    } catch (err) {
        console.error('Error fetching dashboard stats:', err);
    }
}

async function loadRecentIssues() {
    const tbody = document.getElementById('recent-issues-table-body');
    try {
        const res = await fetch(`${API_BASE}/issues?status=ALL`);
        if (!res.ok) throw new Error();
        const issues = await res.json();

        if (issues.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No transactions found yet.</td></tr>';
            return;
        }

        // Show top 5 recent
        const recent = issues.slice(0, 5);
        tbody.innerHTML = recent.map(iss => {
            const isOverdue = iss.status === 'ISSUED' && iss.daysOverdue > 0;
            const badgeClass = isOverdue ? 'badge-danger' : (iss.status === 'RETURNED' ? 'badge-secondary' : 'badge-success');
            const statusLabel = isOverdue ? `OVERDUE (${iss.daysOverdue}d)` : iss.status;

            return `
                <tr>
                    <td><strong>${escapeHtml(iss.bookTitle)}</strong></td>
                    <td>${escapeHtml(iss.memberName)}</td>
                    <td>${iss.issueDate}</td>
                    <td>${iss.dueDate}</td>
                    <td><span class="badge ${badgeClass}">${statusLabel}</span></td>
                </tr>
            `;
        }).join('');
    } catch (e) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">Could not load recent activity.</td></tr>';
    }
}

// --- Books Management ---
let bookSearchDebounce;
function handleBookSearch() {
    clearTimeout(bookSearchDebounce);
    bookSearchDebounce = setTimeout(() => {
        const q = document.getElementById('book-search-input').value;
        loadBooks(q);
    }, 300);
}

async function loadBooks(search = '') {
    const tbody = document.getElementById('books-table-body');
    tbody.innerHTML = '<tr><td colspan="9" class="text-center">Loading books...</td></tr>';

    try {
        const url = search ? `${API_BASE}/books?search=${encodeURIComponent(search)}` : `${API_BASE}/books`;
        const res = await fetch(url);
        const books = await res.json();
        cachedBooks = books;

        if (books.length === 0) {
            tbody.innerHTML = '<tr><td colspan="9" class="text-center text-muted">No books found. Click "+ Add New Book" to add one!</td></tr>';
            return;
        }

        tbody.innerHTML = books.map(b => {
            const inStock = b.availableCopies > 0;
            const stockBadge = inStock 
                ? `<span class="badge badge-success">In Stock (${b.availableCopies})</span>` 
                : `<span class="badge badge-danger">Out of Stock</span>`;

            return `
                <tr>
                    <td>#${b.id}</td>
                    <td><code>${escapeHtml(b.isbn)}</code></td>
                    <td><strong>${escapeHtml(b.title)}</strong></td>
                    <td>${escapeHtml(b.author)}</td>
                    <td><span class="badge badge-secondary">${escapeHtml(b.category)}</span></td>
                    <td>${b.totalCopies}</td>
                    <td><strong>${b.availableCopies}</strong></td>
                    <td>${stockBadge}</td>
                    <td>
                        <button class="btn btn-danger-outline" onclick="deleteBook(${b.id}, '${escapeQuote(b.title)}')">Delete</button>
                    </td>
                </tr>
            `;
        }).join('');
    } catch (err) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center text-muted">Error loading books. Verify backend connection.</td></tr>';
    }
}

async function submitAddBook(event) {
    event.preventDefault();
    const payload = {
        title: document.getElementById('book-title').value.trim(),
        author: document.getElementById('book-author').value.trim(),
        isbn: document.getElementById('book-isbn').value.trim(),
        category: document.getElementById('book-category').value.trim(),
        totalCopies: parseInt(document.getElementById('book-copies').value, 10) || 1
    };

    try {
        const res = await fetch(`${API_BASE}/books`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();

        if (!res.ok) throw new Error(data.message || 'Failed to add book');

        showToast('Book added successfully!', 'success');
        closeModal('addBookModal');
        document.getElementById('addBookForm').reset();
        loadBooks();
        loadDashboard();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function deleteBook(id, title) {
    if (!confirm(`Are you sure you want to delete "${title}"?`)) return;

    try {
        const res = await fetch(`${API_BASE}/books?id=${id}`, { method: 'DELETE' });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Could not delete book');

        showToast('Book deleted successfully', 'success');
        loadBooks();
        loadDashboard();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// --- Members Management ---
let memberSearchDebounce;
function handleMemberSearch() {
    clearTimeout(memberSearchDebounce);
    memberSearchDebounce = setTimeout(() => {
        const q = document.getElementById('member-search-input').value;
        loadMembers(q);
    }, 300);
}

async function loadMembers(search = '') {
    const tbody = document.getElementById('members-table-body');
    tbody.innerHTML = '<tr><td colspan="8" class="text-center">Loading members...</td></tr>';

    try {
        const url = search ? `${API_BASE}/members?search=${encodeURIComponent(search)}` : `${API_BASE}/members`;
        const res = await fetch(url);
        const members = await res.json();
        cachedMembers = members;

        if (members.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">No members found. Register a member first!</td></tr>';
            return;
        }

        tbody.innerHTML = members.map(m => `
            <tr>
                <td>#${m.id}</td>
                <td><code>${escapeHtml(m.memberCode)}</code></td>
                <td><strong>${escapeHtml(m.name)}</strong></td>
                <td>${escapeHtml(m.email)}</td>
                <td>${escapeHtml(m.phone || 'N/A')}</td>
                <td>${m.membershipDate}</td>
                <td><span class="badge badge-success">${m.status}</span></td>
                <td>
                    <button class="btn btn-danger-outline" onclick="deleteMember(${m.id}, '${escapeQuote(m.name)}')">Delete</button>
                </td>
            </tr>
        `).join('');
    } catch (err) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">Error loading members.</td></tr>';
    }
}

async function submitAddMember(event) {
    event.preventDefault();
    const payload = {
        name: document.getElementById('member-name').value.trim(),
        email: document.getElementById('member-email').value.trim(),
        phone: document.getElementById('member-phone').value.trim(),
        memberCode: document.getElementById('member-code').value.trim() || undefined
    };

    try {
        const res = await fetch(`${API_BASE}/members`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();

        if (!res.ok) throw new Error(data.message || 'Failed to register member');

        showToast('Member registered successfully!', 'success');
        closeModal('addMemberModal');
        document.getElementById('addMemberForm').reset();
        loadMembers();
        loadDashboard();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function deleteMember(id, name) {
    if (!confirm(`Are you sure you want to remove member "${name}"?`)) return;

    try {
        const res = await fetch(`${API_BASE}/members?id=${id}`, { method: 'DELETE' });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Could not delete member');

        showToast('Member deleted successfully', 'success');
        loadMembers();
        loadDashboard();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// --- Issue & Return System ---
function filterIssues(status, btnElement) {
    currentIssueFilter = status;
    document.querySelectorAll('.filter-pill').forEach(b => b.classList.remove('active'));
    if (btnElement) btnElement.classList.add('active');
    loadIssues(status);
}

async function loadIssues(status = 'ALL') {
    const tbody = document.getElementById('issues-table-body');
    tbody.innerHTML = '<tr><td colspan="9" class="text-center">Loading transactions...</td></tr>';

    try {
        const res = await fetch(`${API_BASE}/issues?status=${status}`);
        const issues = await res.json();

        if (issues.length === 0) {
            tbody.innerHTML = `<tr><td colspan="9" class="text-center text-muted">No ${status.toLowerCase()} records found.</td></tr>`;
            return;
        }

        tbody.innerHTML = issues.map(iss => {
            const isOverdue = iss.status === 'ISSUED' && iss.daysOverdue > 0;
            let statusBadge;
            if (iss.status === 'RETURNED') {
                statusBadge = '<span class="badge badge-secondary">RETURNED</span>';
            } else if (isOverdue) {
                statusBadge = `<span class="badge badge-danger">OVERDUE (+${iss.daysOverdue} days)</span>`;
            } else {
                statusBadge = '<span class="badge badge-success">ACTIVE</span>';
            }

            const actionBtn = iss.status === 'ISSUED' 
                ? `<button class="btn btn-primary btn-action" onclick="openReturnModal(${iss.id}, '${escapeQuote(iss.bookTitle)}', '${escapeQuote(iss.memberName)}', '${iss.dueDate}', ${iss.daysOverdue})">Return Book</button>`
                : `<span class="text-muted">Completed</span>`;

            return `
                <tr>
                    <td>#${iss.id}</td>
                    <td><strong>${escapeHtml(iss.bookTitle)}</strong><br><small class="text-muted">${iss.bookIsbn}</small></td>
                    <td>${escapeHtml(iss.memberName)}<br><small class="text-muted">${iss.memberCode}</small></td>
                    <td>${iss.issueDate}</td>
                    <td>${iss.dueDate}</td>
                    <td>${iss.returnDate ? iss.returnDate : '<span class="text-muted">Pending</span>'}</td>
                    <td><strong>$${Number(iss.fineAmount).toFixed(2)}</strong></td>
                    <td>${statusBadge}</td>
                    <td>${actionBtn}</td>
                </tr>
            `;
        }).join('');
    } catch (err) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center text-muted">Error loading issue records.</td></tr>';
    }
}

// --- Issue Modal Setup & Submission ---
async function openIssueModal() {
    try {
        // Fetch fresh books and members
        const [booksRes, membersRes] = await Promise.all([
            fetch(`${API_BASE}/books`),
            fetch(`${API_BASE}/members`)
        ]);
        const books = await booksRes.json();
        const members = await membersRes.json();

        const bookSelect = document.getElementById('issue-book-select');
        const memberSelect = document.getElementById('issue-member-select');

        // Populate Books (only available ones)
        const availableBooks = books.filter(b => b.availableCopies > 0);
        bookSelect.innerHTML = availableBooks.length === 0
            ? '<option value="">No books currently available</option>'
            : '<option value="">-- Choose a book --</option>' + availableBooks.map(b => 
                `<option value="${b.id}">${escapeHtml(b.title)} (${b.availableCopies} available)</option>`
              ).join('');

        // Populate Members
        memberSelect.innerHTML = members.length === 0
            ? '<option value="">No members registered</option>'
            : '<option value="">-- Choose a member --</option>' + members.map(m => 
                `<option value="${m.id}">${escapeHtml(m.name)} (${m.memberCode})</option>`
              ).join('');

        openModal('issueBookModal');
    } catch (err) {
        showToast('Could not load books or members for issuing', 'error');
    }
}

async function submitIssueBook(event) {
    event.preventDefault();
    const payload = {
        bookId: parseInt(document.getElementById('issue-book-select').value, 10),
        memberId: parseInt(document.getElementById('issue-member-select').value, 10),
        daysDue: parseInt(document.getElementById('issue-period-select').value, 10) || 14,
        notes: document.getElementById('issue-notes').value.trim()
    };

    if (!payload.bookId || !payload.memberId) {
        showToast('Please select both a book and a member', 'error');
        return;
    }

    try {
        const res = await fetch(`${API_BASE}/issues/borrow`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();

        if (!res.ok) throw new Error(data.message || 'Failed to issue book');

        showToast('Book issued successfully!', 'success');
        closeModal('issueBookModal');
        document.getElementById('issueBookForm').reset();
        loadDashboard();
        loadIssues(currentIssueFilter);
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// --- Return Book Modal & Submission ---
function openReturnModal(issueId, bookTitle, memberName, dueDate, daysOverdue) {
    document.getElementById('return-issue-id').value = issueId;
    document.getElementById('return-book-title').innerText = bookTitle;
    document.getElementById('return-member-name').innerText = memberName;
    document.getElementById('return-due-date').innerText = dueDate;

    const overdueBanner = document.getElementById('overdue-banner');
    const fineInput = document.getElementById('return-fine-input');

    if (daysOverdue > 0) {
        overdueBanner.style.display = 'block';
        document.getElementById('return-days-overdue').innerText = daysOverdue;
        const calculatedFine = (daysOverdue * 2.0).toFixed(2);
        fineInput.value = calculatedFine;
    } else {
        overdueBanner.style.display = 'none';
        fineInput.value = '0.00';
    }

    openModal('returnBookModal');
}

async function confirmReturnBook() {
    const issueId = parseInt(document.getElementById('return-issue-id').value, 10);
    const fine = parseFloat(document.getElementById('return-fine-input').value) || 0.0;

    try {
        const res = await fetch(`${API_BASE}/issues/return`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ issueId, fineAmount: fine })
        });
        const data = await res.json();

        if (!res.ok) throw new Error(data.message || 'Failed to process return');

        showToast('Book returned successfully!', 'success');
        closeModal('returnBookModal');
        loadDashboard();
        loadIssues(currentIssueFilter);
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// --- Modal Helper Functions ---
function openModal(id) {
    const m = document.getElementById(id);
    if (m) m.classList.add('open');
}

function closeModal(id) {
    const m = document.getElementById(id);
    if (m) m.classList.remove('open');
}

window.addEventListener('click', (e) => {
    if (e.target.classList.contains('modal-overlay')) {
        e.target.classList.remove('open');
    }
});

// --- Toast Notifications ---
function showToast(message, type = 'success') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    const icon = type === 'success' ? '✅' : '❌';
    toast.innerHTML = `<span>${icon}</span> <span>${escapeHtml(message)}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(100%)';
        toast.style.transition = 'all 0.3s ease';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

// --- Security String Sanitizers ---
function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function escapeQuote(str) {
    if (!str) return '';
    return String(str).replace(/'/g, "\\'").replace(/"/g, '&quot;');
}
