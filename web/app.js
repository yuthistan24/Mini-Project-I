// ==========================================================================
// Vaanam Airport Baggage Management System — Frontend Application (Vanilla JS)
// ==========================================================================

document.addEventListener('DOMContentLoaded', () => {
    // Application State
    const state = {
        currentUser: null,
        currentRole: null,
        currentView: 'dashboard',
        baggageList: [],
        flightList: [],
        conveyorList: [],
        bagFilter: 'ALL',
        searchQuery: ''
    };

    // DOM Elements
    const loginScreen = document.getElementById('login-screen');
    const registerScreen = document.getElementById('register-screen');
    const dashboardScreen = document.getElementById('dashboard-screen');
    const pageHeading = document.getElementById('page-heading');
    const simulatedClockTime = document.getElementById('simulated-clock-time');

    // ==================== INITIALIZATION ====================

    checkExistingSession();
    setupNavigation();
    setupAuthListeners();
    setupBaggageListeners();
    setupFlightListeners();
    setupConveyorListeners();
    setupSecurityListeners();
    setupUserManagementListeners();
    setupSimulatedClock();

    // ==================== SESSION & AUTH ====================

    async function checkExistingSession() {
        try {
            const res = await fetch('/api/session');
            if (res.ok) {
                const data = await res.json();
                setCurrentUser(data.username, data.role);
                showDashboard();
            } else {
                showLogin();
            }
        } catch (e) {
            showLogin();
        }
    }

    function setCurrentUser(username, role) {
        state.currentUser = username;
        state.currentRole = role;

        document.getElementById('current-user-name').innerText = username;
        document.getElementById('current-user-role').innerText = role;
        document.getElementById('avatar-initials').innerText = username.substring(0, 2).toUpperCase();

        // Role-based navigation visibility
        const isSecurity = (role === 'ADMIN' || role === 'SECURITY_OFFICER');
        const isUserAdmin = (role === 'ADMIN' || role === 'AUDITOR');

        document.getElementById('nav-item-security').style.display = isSecurity ? 'block' : 'none';
        document.getElementById('nav-item-users').style.display = isUserAdmin ? 'block' : 'none';

        // Operator/Supervisor specific controls
        const canProcess = (role === 'ADMIN' || role === 'BAGGAGE_OPERATOR');
        const processBtn = document.getElementById('btn-process-next-bag');
        const regBagBtn = document.getElementById('btn-open-register-bag');
        if (processBtn) processBtn.style.display = canProcess ? 'inline-flex' : 'none';
        if (regBagBtn) regBagBtn.style.display = canProcess ? 'inline-flex' : 'none';
    }

    function showLogin() {
        loginScreen.classList.add('active');
        registerScreen.classList.remove('active');
        dashboardScreen.classList.remove('active');
    }

    function showDashboard() {
        loginScreen.classList.remove('active');
        registerScreen.classList.remove('active');
        dashboardScreen.classList.add('active');
        switchView('dashboard');
        loadAllData();
    }

    function setupAuthListeners() {
        // Toggle password visibility
        const toggleBtn = document.getElementById('toggle-password');
        const passInput = document.getElementById('password');
        if (toggleBtn && passInput) {
            toggleBtn.addEventListener('click', () => {
                const isPassword = passInput.type === 'password';
                passInput.type = isPassword ? 'text' : 'password';
                toggleBtn.innerText = isPassword ? '🙈' : '👁';
            });
        }

        // Login submit
        document.getElementById('login-btn').addEventListener('click', handleLogin);
        document.getElementById('password').addEventListener('keypress', (e) => {
            if (e.key === 'Enter') handleLogin();
        });

        // Logout
        document.getElementById('logout-btn').addEventListener('click', async () => {
            await fetch('/api/logout', { method: 'POST' });
            state.currentUser = null;
            state.currentRole = null;
            showLogin();
            showToast('Logged out successfully', 'info');
        });

        // Navigation between login & register
        document.getElementById('show-register-link').addEventListener('click', (e) => {
            e.preventDefault();
            loginScreen.classList.remove('active');
            registerScreen.classList.add('active');
            document.getElementById('register-error').style.display = 'none';
            document.getElementById('register-success').style.display = 'none';
        });

        document.getElementById('back-to-login-btn').addEventListener('click', () => {
            registerScreen.classList.remove('active');
            loginScreen.classList.add('active');
        });

        // Register submit
        document.getElementById('register-btn').addEventListener('click', handleRegister);
    }

    async function handleLogin() {
        const username = document.getElementById('username').value.trim();
        const password = document.getElementById('password').value;
        const errDiv = document.getElementById('login-error');
        const loginBtn = document.getElementById('login-btn');

        errDiv.style.display = 'none';
        if (!username || !password) {
            errDiv.innerText = 'Please enter both username and password.';
            errDiv.style.display = 'block';
            return;
        }

        loginBtn.disabled = true;
        loginBtn.querySelector('.btn-text').innerText = 'Signing in...';

        try {
            const res = await fetch('/api/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });

            if (res.ok) {
                const data = await res.json();
                setCurrentUser(data.username, data.role);
                document.getElementById('password').value = '';
                showDashboard();
                showToast(`Welcome back, ${data.username}!`, 'success');
            } else {
                const errData = await res.json().catch(() => ({}));
                errDiv.innerText = errData.error || 'Invalid credentials or inactive account.';
                errDiv.style.display = 'block';
            }
        } catch (e) {
            errDiv.innerText = 'Unable to connect to server. Ensure backend is running.';
            errDiv.style.display = 'block';
        } finally {
            loginBtn.disabled = false;
            loginBtn.querySelector('.btn-text').innerText = 'Sign In';
        }
    }

    async function handleRegister() {
        const username = document.getElementById('reg-username').value.trim();
        const password = document.getElementById('reg-password').value;
        const confirm = document.getElementById('reg-confirm').value;
        const role = document.getElementById('reg-role').value;
        const errDiv = document.getElementById('register-error');
        const succDiv = document.getElementById('register-success');

        errDiv.style.display = 'none';
        succDiv.style.display = 'none';

        if (!username || !password || !confirm) {
            errDiv.innerText = 'All fields are required.';
            errDiv.style.display = 'block';
            return;
        }

        if (password !== confirm) {
            errDiv.innerText = 'Passwords do not match.';
            errDiv.style.display = 'block';
            return;
        }

        try {
            const res = await fetch('/api/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password, role })
            });

            const data = await res.json();
            if (res.ok) {
                succDiv.innerText = 'Account successfully provisioned! You may now sign in.';
                succDiv.style.display = 'block';
                setTimeout(() => {
                    registerScreen.classList.remove('active');
                    loginScreen.classList.add('active');
                    document.getElementById('username').value = username;
                    document.getElementById('password').focus();
                }, 1500);
            } else {
                errDiv.innerText = data.error || 'Registration failed.';
                errDiv.style.display = 'block';
            }
        } catch (e) {
            errDiv.innerText = 'Connection error.';
            errDiv.style.display = 'block';
        }
    }

    // ==================== NAVIGATION ====================

    function setupNavigation() {
        const navItems = document.querySelectorAll('.nav-menu .nav-item');
        navItems.forEach(item => {
            item.addEventListener('click', (e) => {
                e.preventDefault();
                const target = item.getAttribute('data-target');
                navItems.forEach(i => i.classList.remove('active'));
                item.classList.add('active');
                switchView(target);
            });
        });

        // Quick shortcuts from dashboard
        document.getElementById('dash-view-all-flights').addEventListener('click', () => {
            document.querySelector('[data-target="flights"]').click();
        });
        document.getElementById('dash-view-all-audit').addEventListener('click', () => {
            document.querySelector('[data-target="users"]').click();
        });
    }

    function switchView(viewName) {
        state.currentView = viewName;
        const views = document.querySelectorAll('.content-view');
        views.forEach(v => v.classList.remove('active'));

        const targetView = document.getElementById('view-' + viewName);
        if (targetView) targetView.classList.add('active');

        const titles = {
            'dashboard': 'Airport Operations Dashboard',
            'baggage': 'Baggage Handling & Routing Operations',
            'flights': 'Flight Schedule & Loading Bay Assignments',
            'conveyors': 'Conveyor Network Routing & Availability',
            'security': 'Security Screening & Clearance Hub',
            'users': 'Staff Access Management & Audit Logs'
        };
        pageHeading.innerText = titles[viewName] || 'Airport Operations';

        // Refresh view data
        if (viewName === 'dashboard') loadDashboardMetrics();
        else if (viewName === 'baggage') loadBaggageList();
        else if (viewName === 'flights') loadFlightsList();
        else if (viewName === 'conveyors') loadConveyorsList();
        else if (viewName === 'security') loadSecurityList();
        else if (viewName === 'users') loadUsersAndAuditList();
    }

    // ==================== DATA LOADING ====================

    async function loadAllData() {
        await Promise.all([
            loadDashboardMetrics(),
            loadBaggageList(),
            loadFlightsList(),
            loadConveyorsList(),
            loadSimulatedTime()
        ]);
    }

    async function loadDashboardMetrics() {
        try {
            const res = await fetch('/api/stats');
            if (res.ok) {
                const stats = await res.json();
                document.getElementById('dash-stat-total').innerText = stats.total;
                document.getElementById('dash-stat-clearance').innerText = stats.awaitingClearance;
                document.getElementById('dash-stat-queued').innerText = stats.queued;
                document.getElementById('dash-stat-loaded').innerText = stats.loaded + stats.claimed;
                document.getElementById('dash-stat-missed').innerText = stats.missedCutoff;
                document.getElementById('dash-stat-conveyors').innerText = `${stats.availableConveyors} / ${stats.totalConveyors}`;

                if (stats.formattedTime) {
                    simulatedClockTime.innerText = stats.formattedTime;
                }
            }

            // Also load quick tables on dashboard
            loadDashboardQuickTables();
        } catch (e) {
            console.error('Failed to load stats', e);
        }
    }

    async function loadDashboardQuickTables() {
        // Quick flights
        try {
            const fRes = await fetch('/api/flights');
            if (fRes.ok) {
                const flights = await fRes.json();
                const tbody = document.querySelector('#dash-flights-table tbody');
                tbody.innerHTML = '';
                flights.slice(0, 5).forEach(f => {
                    const tr = document.createElement('tr');
                    tr.innerHTML = `
                        <td><strong>${escapeHtml(f.flightId)}</strong></td>
                        <td>${escapeHtml(f.destination)}</td>
                        <td>${f.formattedCutoff}</td>
                        <td><span class="badge badge-blue">${escapeHtml(f.assignedBay)}</span></td>
                    `;
                    tbody.appendChild(tr);
                });
            }
        } catch (e) {}

        // Quick audit
        try {
            const aRes = await fetch('/api/audit');
            if (aRes.ok) {
                const logs = await aRes.json();
                const tbody = document.querySelector('#dash-audit-table tbody');
                tbody.innerHTML = '';
                logs.slice(0, 5).forEach(l => {
                    const tr = document.createElement('tr');
                    const badgeClass = l.result === 'SUCCESS' ? 'badge-green' : 'badge-red';
                    tr.innerHTML = `
                        <td>${escapeHtml(l.timestamp || l.simulatedTime + 'm')}</td>
                        <td>${escapeHtml(l.username)}</td>
                        <td>${escapeHtml(l.action)}</td>
                        <td><span class="badge ${badgeClass}">${escapeHtml(l.result)}</span></td>
                    `;
                    tbody.appendChild(tr);
                });
            }
        } catch (e) {}
    }

    // ==================== BAGGAGE OPS ====================

    async function loadBaggageList() {
        try {
            const res = await fetch('/api/baggage');
            if (res.ok) {
                state.baggageList = await res.json();
                renderBaggageTable();
            }
        } catch (e) {
            console.error('Failed to load baggage list', e);
        }
    }

    function renderBaggageTable() {
        const tbody = document.getElementById('baggage-table-body');
        tbody.innerHTML = '';

        let filtered = state.baggageList.filter(b => {
            // Filter tabs
            if (state.bagFilter === 'OUTGOING' && b.direction !== 'OUTGOING') return false;
            if (state.bagFilter === 'INCOMING' && b.direction !== 'INCOMING') return false;
            if (state.bagFilter === 'TRANSFER' && b.direction !== 'TRANSFER') return false;
            if (state.bagFilter === 'CLEARANCE' && b.status !== 'WAITING_FOR_CLEARANCE') return false;

            // Search query
            if (state.searchQuery && !b.bagId.toLowerCase().includes(state.searchQuery.toLowerCase())) {
                return false;
            }
            return true;
        });

        if (filtered.length === 0) {
            tbody.innerHTML = `<tr><td colspan="10" style="text-align:center; padding: 2rem; color: #94a3b8;">No matching baggage records found.</td></tr>`;
            return;
        }

        filtered.forEach(b => {
            const tr = document.createElement('tr');

            // Status badge class
            let statusBadge = 'badge-gray';
            if (b.status === 'LOADED' || b.status === 'CLAIMED') statusBadge = 'badge-green';
            else if (b.status === 'QUEUED') statusBadge = 'badge-blue';
            else if (b.status === 'WAITING_FOR_CLEARANCE') statusBadge = 'badge-amber';
            else if (b.status === 'MISSED_CUTOFF' || b.status === 'REJECTED') statusBadge = 'badge-red';
            else if (b.status === 'ROUTING') statusBadge = 'badge-purple';

            // Clearance indicator
            const clearanceHtml = b.direction === 'INCOMING' 
                ? '<span class="badge badge-gray">N/A (Inbound)</span>'
                : (b.cleared ? '<span class="badge badge-green">Approved</span>' : '<span class="badge badge-amber">Pending</span>');

            tr.innerHTML = `
                <td><strong>${escapeHtml(b.bagId)}</strong></td>
                <td><span class="badge badge-blue">${escapeHtml(b.bagType || b.direction)}</span></td>
                <td>${escapeHtml(b.category)}</td>
                <td>${escapeHtml(b.flightId)}</td>
                <td>${escapeHtml(b.currentLocation)}</td>
                <td><strong>${escapeHtml(b.destinationNode || '-')}</strong></td>
                <td>${escapeHtml(b.cutoff)}</td>
                <td>${clearanceHtml}</td>
                <td><span class="badge ${statusBadge}">${escapeHtml(b.status)}</span></td>
                <td>
                    <button class="btn btn-sm btn-outline btn-track-bag" data-id="${escapeHtml(b.bagId)}">🗺️ Track</button>
                    ${(state.currentRole === 'ADMIN' || state.currentRole === 'BAGGAGE_OPERATOR') && (b.status === 'QUEUED' || b.status === 'WAITING_FOR_ASSIGNMENT') 
                        ? `<button class="btn btn-sm btn-success btn-process-single-bag" data-id="${escapeHtml(b.bagId)}">▶ Process</button>` 
                        : ''}
                </td>
            `;
            tbody.appendChild(tr);
        });

        // Attach action handlers
        document.querySelectorAll('.btn-track-bag').forEach(btn => {
            btn.addEventListener('click', () => openTrackingModal(btn.getAttribute('data-id')));
        });
        document.querySelectorAll('.btn-process-single-bag').forEach(btn => {
            btn.addEventListener('click', () => processSpecificBag(btn.getAttribute('data-id')));
        });
    }

    function setupBaggageListeners() {
        // Tab filters
        document.querySelectorAll('.filter-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                document.querySelectorAll('.filter-tab').forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                state.bagFilter = tab.getAttribute('data-filter');
                renderBaggageTable();
            });
        });

        // Search input
        document.getElementById('baggage-search-input').addEventListener('input', (e) => {
            state.searchQuery = e.target.value.trim();
            renderBaggageTable();
        });

        document.getElementById('baggage-refresh-btn').addEventListener('click', loadBaggageList);

        // Process next bag in priority queue
        document.getElementById('btn-process-next-bag').addEventListener('click', async () => {
            try {
                const res = await fetch('/api/baggage/process', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({})
                });
                const data = await res.json();
                if (res.ok) {
                    if (data.status === 'Queue is empty') {
                        showToast('Priority Queue is currently empty.', 'info');
                    } else {
                        showToast(`Bag ${data.bagId} processed: ${data.bagStatus} (ETA: ${data.estimatedTime}m)`, 'success');
                        loadBaggageList();
                        loadDashboardMetrics();
                    }
                } else {
                    showToast(data.error || 'Processing failed', 'error');
                }
            } catch (e) {
                showToast('Failed to connect to processing engine', 'error');
            }
        });

        // Register baggage modal
        const regModal = document.getElementById('modal-register-bag');
        document.getElementById('btn-open-register-bag').addEventListener('click', () => {
            populateFlightSelectForRegistration();
            regModal.style.display = 'flex';
        });

        document.getElementById('modal-reg-bag-close').addEventListener('click', () => {
            regModal.style.display = 'none';
        });
        document.getElementById('modal-reg-bag-cancel').addEventListener('click', () => {
            regModal.style.display = 'none';
        });

        // Change fields based on direction (Incoming vs Outgoing/Transfer)
        document.getElementById('new-bag-direction').addEventListener('change', (e) => {
            const dir = e.target.value;
            const isIncoming = (dir === 'INCOMING');
            document.getElementById('group-flight-select').style.display = isIncoming ? 'none' : 'block';
            document.getElementById('group-dest-select').style.display = isIncoming ? 'block' : 'none';
        });

        // Submit registration
        document.getElementById('modal-reg-bag-submit').addEventListener('click', async () => {
            const bagId = document.getElementById('new-bag-id').value.trim();
            const direction = document.getElementById('new-bag-direction').value;
            const catRank = document.getElementById('new-bag-category').value;
            const flightId = document.getElementById('new-bag-flight').value;
            const destNode = document.getElementById('new-bag-dest').value;
            const errBox = document.getElementById('register-bag-error');

            errBox.style.display = 'none';
            if (!bagId) {
                errBox.innerText = 'Baggage Barcode ID is required.';
                errBox.style.display = 'block';
                return;
            }

            try {
                const res = await fetch('/api/baggage/register', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        bagId,
                        direction,
                        categoryRank: catRank,
                        flightId: direction === 'INCOMING' ? '' : flightId,
                        destinationNode: direction === 'INCOMING' ? destNode : ''
                    })
                });

                const data = await res.json();
                if (res.ok) {
                    showToast(`Baggage ${bagId} registered successfully!`, 'success');
                    regModal.style.display = 'none';
                    document.getElementById('new-bag-id').value = '';
                    loadBaggageList();
                    loadDashboardMetrics();
                } else {
                    errBox.innerText = data.error || 'Failed to register baggage.';
                    errBox.style.display = 'block';
                }
            } catch (e) {
                errBox.innerText = 'Connection error.';
                errBox.style.display = 'block';
            }
        });
    }

    async function processSpecificBag(bagId) {
        try {
            const res = await fetch('/api/baggage/process', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ bagId })
            });
            const data = await res.json();
            if (res.ok) {
                showToast(`Bag ${bagId} processed: ${data.bagStatus}`, 'success');
                loadBaggageList();
                loadDashboardMetrics();
            } else {
                showToast(data.error || 'Failed to process bag', 'error');
            }
        } catch (e) {
            showToast('Connection error', 'error');
        }
    }

    async function populateFlightSelectForRegistration() {
        const select = document.getElementById('new-bag-flight');
        select.innerHTML = '';
        try {
            const res = await fetch('/api/flights');
            if (res.ok) {
                const flights = await res.json();
                flights.forEach(f => {
                    const opt = document.createElement('option');
                    opt.value = f.flightId;
                    opt.innerText = `${f.flightId} → ${f.destination} (Cutoff: ${f.formattedCutoff}, Bay: ${f.assignedBay})`;
                    select.appendChild(opt);
                });
            }
        } catch (e) {}
    }

    // ==================== VISUAL ROUTE TRACKING MODAL ====================

    async function openTrackingModal(bagId) {
        const modal = document.getElementById('modal-tracking');
        try {
            const res = await fetch('/api/baggage/track?id=' + encodeURIComponent(bagId));
            if (!res.ok) {
                showToast('Failed to fetch tracking data', 'error');
                return;
            }

            const data = await res.json();

            document.getElementById('track-bag-id').innerText = data.bagId;
            document.getElementById('track-bag-type').innerText = data.bagType || data.direction;
            document.getElementById('track-flight-id').innerText = data.flightId;
            document.getElementById('track-status-badge').innerText = data.status;
            document.getElementById('track-eta').innerText = data.estimatedTime >= 0 ? `${data.estimatedTime} minutes` : 'No valid route';
            document.getElementById('track-cutoff').innerText = data.cutoff || 'N/A';

            // Status badge coloring
            const badge = document.getElementById('track-status-badge');
            badge.className = 'badge';
            if (data.status === 'LOADED' || data.status === 'CLAIMED') badge.classList.add('badge-green');
            else if (data.status === 'QUEUED') badge.classList.add('badge-blue');
            else if (data.status === 'WAITING_FOR_CLEARANCE') badge.classList.add('badge-amber');
            else if (data.status === 'MISSED_CUTOFF' || data.status === 'REJECTED') badge.classList.add('badge-red');
            else badge.classList.add('badge-purple');

            // Render visual connected-node route
            const routeContainer = document.getElementById('visual-route-path');
            routeContainer.innerHTML = '';

            const route = data.route || [];
            if (route.length === 0) {
                routeContainer.innerHTML = `<div style="padding: 1.5rem; text-align: center; color: #ef4444; width: 100%;">⚠️ No active conveyor route pathway found for this destination or destination is blocked.</div>`;
            } else {
                const currentIdx = route.indexOf(data.currentLocation);

                route.forEach((node, idx) => {
                    const stepDiv = document.createElement('div');
                    stepDiv.className = 'route-node-step';

                    // Node state
                    let stateClass = 'dot-upcoming';
                    if (data.status === 'LOADED' || data.status === 'CLAIMED') {
                        stateClass = (idx === route.length - 1) ? 'state-dest' : 'state-cleared';
                    } else if (idx < currentIdx) {
                        stateClass = 'state-cleared';
                    } else if (idx === currentIdx) {
                        stateClass = 'state-current';
                    } else if (idx === route.length - 1) {
                        stateClass = 'state-dest';
                    }

                    stepDiv.innerHTML = `
                        <div class="node-circle ${stateClass}">${idx + 1}</div>
                        <div class="node-name">${escapeHtml(node)}</div>
                    `;
                    routeContainer.appendChild(stepDiv);

                    // Arrow between nodes
                    if (idx < route.length - 1) {
                        const arrow = document.createElement('div');
                        arrow.className = 'route-arrow';
                        arrow.innerHTML = '➔';
                        routeContainer.appendChild(arrow);
                    }
                });
            }

            modal.style.display = 'flex';
        } catch (e) {
            showToast('Error opening tracking window', 'error');
        }
    }

    document.getElementById('modal-tracking-close').addEventListener('click', () => {
        document.getElementById('modal-tracking').style.display = 'none';
    });
    document.getElementById('modal-tracking-btn-close').addEventListener('click', () => {
        document.getElementById('modal-tracking').style.display = 'none';
    });

    // ==================== FLIGHTS & BAYS ====================

    async function loadFlightsList() {
        try {
            const res = await fetch('/api/flights');
            if (res.ok) {
                state.flightList = await res.json();
                renderFlightsTable();
            }
            loadBaysGrid();
        } catch (e) {
            console.error('Failed to load flights', e);
        }
    }

    function renderFlightsTable() {
        const tbody = document.getElementById('flights-full-body');
        tbody.innerHTML = '';

        const canEdit = (state.currentRole === 'ADMIN' || state.currentRole === 'SUPERVISOR');

        state.flightList.forEach(f => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><strong>${escapeHtml(f.flightId)}</strong></td>
                <td>${escapeHtml(f.destination)}</td>
                <td>${escapeHtml(f.formattedCutoff)} (${f.cutoff}m)</td>
                <td><span class="badge badge-blue">${escapeHtml(f.assignedBay)}</span></td>
                <td>
                    ${canEdit ? `<button class="btn btn-sm btn-outline btn-edit-flight" data-id="${escapeHtml(f.flightId)}" data-bay="${escapeHtml(f.assignedBay)}" data-cutoff="${f.cutoff}">✏️ Edit Bay / Cutoff</button>` : '<span class="subtext">Read-only</span>'}
                </td>
            `;
            tbody.appendChild(tr);
        });

        document.querySelectorAll('.btn-edit-flight').forEach(btn => {
            btn.addEventListener('click', () => {
                const flightId = btn.getAttribute('data-id');
                const bay = btn.getAttribute('data-bay');
                const cutoff = btn.getAttribute('data-cutoff');

                document.getElementById('update-flight-id').value = flightId;
                document.getElementById('update-flight-bay').value = bay;
                document.getElementById('update-flight-cutoff').value = cutoff;

                document.getElementById('modal-update-flight').style.display = 'flex';
            });
        });
    }

    async function loadBaysGrid() {
        try {
            const res = await fetch('/api/bays');
            if (res.ok) {
                const bays = await res.json();
                const container = document.getElementById('bays-container');
                container.innerHTML = '';

                bays.forEach(b => {
                    const isOccupied = b.assignedFlight && b.assignedFlight !== 'NONE';
                    const div = document.createElement('div');
                    div.className = `bay-card ${isOccupied ? 'occupied' : ''}`;
                    div.innerHTML = `
                        <div class="bay-title">${escapeHtml(b.bayId)}</div>
                        <div class="mt-2">
                            <span class="subtext">Assigned Flight:</span>
                            <div><strong>${escapeHtml(b.assignedFlight || 'Unassigned')}</strong></div>
                        </div>
                    `;
                    container.appendChild(div);
                });
            }
        } catch (e) {}
    }

    function setupFlightListeners() {
        const modal = document.getElementById('modal-update-flight');
        document.getElementById('modal-flight-close').addEventListener('click', () => modal.style.display = 'none');
        document.getElementById('modal-flight-cancel').addEventListener('click', () => modal.style.display = 'none');

        document.getElementById('modal-flight-submit').addEventListener('click', async () => {
            const flightId = document.getElementById('update-flight-id').value;
            const assignedBay = document.getElementById('update-flight-bay').value;
            const cutoff = document.getElementById('update-flight-cutoff').value;

            try {
                const res = await fetch('/api/flights/update', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ flightId, assignedBay, cutoff })
                });

                if (res.ok) {
                    showToast(`Flight ${flightId} updated successfully`, 'success');
                    modal.style.display = 'none';
                    loadFlightsList();
                    loadBaggageList();
                } else {
                    const err = await res.json();
                    showToast(err.error || 'Update failed', 'error');
                }
            } catch (e) {
                showToast('Connection error', 'error');
            }
        });
    }

    // ==================== CONVEYORS ====================

    async function loadConveyorsList() {
        try {
            const res = await fetch('/api/conveyors');
            if (res.ok) {
                state.conveyorList = await res.json();
                renderConveyorsTable();
            }
        } catch (e) {
            console.error('Failed to load conveyors', e);
        }
    }

    function renderConveyorsTable() {
        const tbody = document.getElementById('conveyors-table-body');
        tbody.innerHTML = '';

        const canToggle = (state.currentRole === 'ADMIN' || state.currentRole === 'SUPERVISOR');

        state.conveyorList.forEach(s => {
            const tr = document.createElement('tr');
            const isAvail = s.available;

            tr.innerHTML = `
                <td><strong>${escapeHtml(s.fromNode)}</strong></td>
                <td><strong>${escapeHtml(s.toNode)}</strong></td>
                <td>⏱️ ${s.processingTime} mins transit</td>
                <td>
                    <span class="badge ${isAvail ? 'badge-green' : 'badge-red'}">
                        ${isAvail ? 'OPERATIONAL' : 'OFFLINE / FAILED'}
                    </span>
                </td>
                <td>
                    ${canToggle ? `
                        <button class="btn btn-sm ${isAvail ? 'btn-danger' : 'btn-success'} btn-toggle-conveyor"
                            data-from="${escapeHtml(s.fromNode)}" data-to="${escapeHtml(s.toNode)}" data-avail="${isAvail}">
                            ${isAvail ? 'Simulate Failure' : 'Restore Online'}
                        </button>
                    ` : '<span class="subtext">Supervisor Only</span>'}
                </td>
            `;
            tbody.appendChild(tr);
        });

        document.querySelectorAll('.btn-toggle-conveyor').forEach(btn => {
            btn.addEventListener('click', async () => {
                const fromNode = btn.getAttribute('data-from');
                const toNode = btn.getAttribute('data-to');
                const currentlyAvail = btn.getAttribute('data-avail') === 'true';

                try {
                    const res = await fetch('/api/conveyors/toggle', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            fromNode,
                            toNode,
                            available: !currentlyAvail
                        })
                    });

                    if (res.ok) {
                        showToast(`Conveyor ${fromNode} ➔ ${toNode} ${!currentlyAvail ? 'restored' : 'marked offline'}. Dynamic Dijkstra rerouting triggered.`, 'info');
                        loadConveyorsList();
                        loadDashboardMetrics();
                        loadBaggageList();
                    } else {
                        const err = await res.json();
                        showToast(err.error || 'Action failed', 'error');
                    }
                } catch (e) {
                    showToast('Connection error', 'error');
                }
            });
        });
    }

    function setupConveyorListeners() {}

    // ==================== SECURITY SCREENING ====================

    async function loadSecurityList() {
        try {
            const res = await fetch('/api/baggage');
            if (res.ok) {
                const allBags = await res.json();
                const pending = allBags.filter(b => b.status === 'WAITING_FOR_CLEARANCE');
                renderSecurityTable(pending);
            }
        } catch (e) {
            console.error('Failed to load security list', e);
        }
    }

    function renderSecurityTable(bags) {
        const tbody = document.getElementById('security-table-body');
        tbody.innerHTML = '';

        if (bags.length === 0) {
            tbody.innerHTML = `<tr><td colspan="5" style="text-align:center; padding: 2rem; color: #10b981;">✅ All registered baggage has passed security screening. No pending bags.</td></tr>`;
            return;
        }

        bags.forEach(b => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><strong>${escapeHtml(b.bagId)}</strong></td>
                <td>${escapeHtml(b.category)}</td>
                <td>${escapeHtml(b.flightId)}</td>
                <td>${escapeHtml(b.currentLocation)}</td>
                <td>
                    <button class="btn btn-sm btn-success btn-approve-sec" data-id="${escapeHtml(b.bagId)}">✓ Approve</button>
                    <button class="btn btn-sm btn-danger btn-reject-sec" data-id="${escapeHtml(b.bagId)}">✕ Reject</button>
                </td>
            `;
            tbody.appendChild(tr);
        });

        document.querySelectorAll('.btn-approve-sec').forEach(btn => {
            btn.addEventListener('click', async () => {
                const bagId = btn.getAttribute('data-id');
                try {
                    const res = await fetch('/api/baggage/clearance', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ bagId, approved: true, reason: 'Security Screening Verified' })
                    });
                    if (res.ok) {
                        showToast(`Baggage ${bagId} approved and queued!`, 'success');
                        loadSecurityList();
                        loadDashboardMetrics();
                    } else {
                        const err = await res.json();
                        showToast(err.error || 'Approval failed', 'error');
                    }
                } catch (e) {
                    showToast('Connection error', 'error');
                }
            });
        });

        document.querySelectorAll('.btn-reject-sec').forEach(btn => {
            btn.addEventListener('click', () => {
                const bagId = btn.getAttribute('data-id');
                document.getElementById('reject-bag-id-display').innerText = bagId;
                document.getElementById('reject-reason-input').value = '';
                document.getElementById('modal-reject-reason').style.display = 'flex';
            });
        });
    }

    function setupSecurityListeners() {
        const rejectModal = document.getElementById('modal-reject-reason');
        document.getElementById('modal-reject-close').addEventListener('click', () => rejectModal.style.display = 'none');
        document.getElementById('modal-reject-cancel').addEventListener('click', () => rejectModal.style.display = 'none');

        document.getElementById('modal-reject-confirm').addEventListener('click', async () => {
            const bagId = document.getElementById('reject-bag-id-display').innerText;
            const reason = document.getElementById('reject-reason-input').value.trim() || 'Suspected prohibited item';

            try {
                const res = await fetch('/api/baggage/clearance', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ bagId, approved: false, reason })
                });

                if (res.ok) {
                    showToast(`Baggage ${bagId} rejected from airport conveyors`, 'info');
                    rejectModal.style.display = 'none';
                    loadSecurityList();
                    loadDashboardMetrics();
                } else {
                    const err = await res.json();
                    showToast(err.error || 'Rejection failed', 'error');
                }
            } catch (e) {
                showToast('Connection error', 'error');
            }
        });
    }

    // ==================== USERS & AUDIT ====================

    async function loadUsersAndAuditList() {
        // Load users if ADMIN
        if (state.currentRole === 'ADMIN') {
            try {
                const res = await fetch('/api/users');
                if (res.ok) {
                    const users = await res.json();
                    renderUsersTable(users);
                }
            } catch (e) {}
        }

        // Load full audit logs
        try {
            const res = await fetch('/api/audit');
            if (res.ok) {
                const logs = await res.json();
                renderAuditTable(logs);
            }
        } catch (e) {}
    }

    function renderUsersTable(users) {
        const tbody = document.getElementById('users-table-body');
        tbody.innerHTML = '';

        users.forEach(u => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><strong>${escapeHtml(u.username)}</strong></td>
                <td><span class="badge badge-blue">${escapeHtml(u.role)}</span></td>
                <td><span class="badge ${u.active ? 'badge-green' : 'badge-red'}">${u.active ? 'ACTIVE' : 'INACTIVE'}</span></td>
                <td>
                    <button class="btn btn-sm btn-outline btn-toggle-user-status" data-user="${escapeHtml(u.username)}" data-active="${u.active}">
                        ${u.active ? 'Deactivate' : 'Activate'}
                    </button>
                    <button class="btn btn-sm btn-outline btn-reset-user-pwd" data-user="${escapeHtml(u.username)}">
                        Reset Pwd
                    </button>
                </td>
            `;
            tbody.appendChild(tr);
        });

        document.querySelectorAll('.btn-toggle-user-status').forEach(btn => {
            btn.addEventListener('click', async () => {
                const username = btn.getAttribute('data-user');
                const currentlyActive = btn.getAttribute('data-active') === 'true';

                try {
                    const res = await fetch('/api/users/update', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            username,
                            action: 'status',
                            value: (!currentlyActive).toString()
                        })
                    });

                    if (res.ok) {
                        showToast(`User ${username} ${!currentlyActive ? 'activated' : 'deactivated'}`, 'info');
                        loadUsersAndAuditList();
                    } else {
                        const err = await res.json();
                        showToast(err.error || 'Action failed', 'error');
                    }
                } catch (e) {
                    showToast('Connection error', 'error');
                }
            });
        });

        document.querySelectorAll('.btn-reset-user-pwd').forEach(btn => {
            btn.addEventListener('click', async () => {
                const username = btn.getAttribute('data-user');
                const newPwd = prompt(`Enter new password for ${username}:`);
                if (!newPwd) return;

                try {
                    const res = await fetch('/api/users/update', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            username,
                            action: 'password',
                            value: newPwd
                        })
                    });

                    if (res.ok) {
                        showToast(`Password reset for ${username}`, 'success');
                    } else {
                        const err = await res.json();
                        showToast(err.error || 'Reset failed', 'error');
                    }
                } catch (e) {
                    showToast('Connection error', 'error');
                }
            });
        });
    }

    function renderAuditTable(logs) {
        const tbody = document.getElementById('audit-full-body');
        tbody.innerHTML = '';

        logs.forEach(l => {
            const tr = document.createElement('tr');
            const badgeClass = l.result === 'SUCCESS' ? 'badge-green' : 'badge-red';
            tr.innerHTML = `
                <td>${l.logId}</td>
                <td>${escapeHtml(l.timestamp || '')}</td>
                <td><strong>${escapeHtml(l.username)}</strong></td>
                <td><span class="badge badge-purple">${escapeHtml(l.action)}</span></td>
                <td>${escapeHtml(l.targetRecord)}</td>
                <td>${l.simulatedTime}m</td>
                <td><span class="badge ${badgeClass}">${escapeHtml(l.result)}</span></td>
                <td>${escapeHtml(l.reason || '-')}</td>
            `;
            tbody.appendChild(tr);
        });
    }

    function setupUserManagementListeners() {
        document.getElementById('btn-admin-add-user').addEventListener('click', () => {
            loginScreen.classList.remove('active');
            registerScreen.classList.add('active');
            dashboardScreen.classList.remove('active');
        });
    }

    // ==================== SIMULATED CLOCK ====================

    async function loadSimulatedTime() {
        try {
            const res = await fetch('/api/time');
            if (res.ok) {
                const data = await res.json();
                simulatedClockTime.innerText = data.formattedTime;
            }
        } catch (e) {}
    }

    function setupSimulatedClock() {
        document.getElementById('advance-clock-btn').addEventListener('click', async () => {
            try {
                const res = await fetch('/api/time/advance', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ minutes: 15 })
                });

                if (res.ok) {
                    const data = await res.json();
                    simulatedClockTime.innerText = data.formattedTime;
                    showToast(`Simulated Clock advanced to ${data.formattedTime}`, 'info');
                    loadAllData();
                } else {
                    const err = await res.json();
                    showToast(err.error || 'Only Supervisors & Admins can advance clock', 'error');
                }
            } catch (e) {
                showToast('Connection error', 'error');
            }
        });
    }

    // ==================== TOAST NOTIFICATION HELPER ====================

    function showToast(message, type = 'info') {
        const container = document.getElementById('toast-container');
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;

        const icons = {
            'success': '✅',
            'error': '⚠️',
            'info': 'ℹ️'
        };

        toast.innerHTML = `<span>${icons[type] || 'ℹ️'}</span> <span>${escapeHtml(message)}</span>`;
        container.appendChild(toast);

        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(50px)';
            toast.style.transition = 'all 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 3500);
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }
});
