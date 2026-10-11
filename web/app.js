// ==========================================================================
// Vaanam Airport Baggage Management System — Frontend Application (Vanilla JS)
// Role-Based Operations: ADMIN, BAGGAGE_MANAGER, PASSENGER
// ==========================================================================

document.addEventListener('DOMContentLoaded', () => {
    // Application State
    const state = {
        currentUser: null,
        currentRole: null,
        clearanceAuthority: false,
        linkedPassengerId: null,
        passengerName: null,
        currentView: 'dashboard',
        baggageList: [],
        passengerList: [],
        flightList: [],
        bayList: [],
        conveyorList: [],
        bagFilter: 'ALL',
        searchQuery: '',
        activeTrackedBagId: null
    };

    // DOM Elements
    const loginScreen = document.getElementById('login-screen');
    const registerScreen = document.getElementById('register-screen');
    const dashboardScreen = document.getElementById('dashboard-screen');
    const pageHeading = document.getElementById('page-heading');
    const pageSubheading = document.getElementById('page-subheading');
    const simulatedClockTime = document.getElementById('simulated-clock-time');

    // ==================== INITIALIZATION ====================

    checkExistingSession();
    setupAuthListeners();
    setupNavigation();
    setupBaggageListeners();
    setupPassengerListeners();
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
                setCurrentUser(data);
                showDashboard();
            } else {
                showLogin();
            }
        } catch (e) {
            showLogin();
        }
    }

    function setCurrentUser(data) {
        state.currentUser = data.username;
        state.currentRole = data.role;
        state.clearanceAuthority = !!data.clearanceAuthority;
        state.linkedPassengerId = data.linkedPassengerId || null;
        state.passengerName = data.passengerName || data.username;

        document.getElementById('current-user-name').innerText = state.passengerName || state.currentUser;
        document.getElementById('current-user-role').innerText = state.currentRole;
        document.getElementById('avatar-initials').innerText = (state.currentUser || 'US').substring(0, 2).toUpperCase();

        // Configure role-based UI permissions & navigation visibility
        const isPassenger = (state.currentRole === 'PASSENGER');
        const isAdmin = (state.currentRole === 'ADMIN');
        const isManager = (state.currentRole === 'BAGGAGE_MANAGER');
        const canClear = state.clearanceAuthority;

        // Navigation elements
        const navPassenger = document.getElementById('nav-item-passenger');
        const navDashboard = document.getElementById('nav-item-dashboard');
        const navBaggage = document.getElementById('nav-item-baggage');
        const navPassengers = document.getElementById('nav-item-passengers');
        const navFlights = document.getElementById('nav-item-flights');
        const navConveyors = document.getElementById('nav-item-conveyors');
        const navSecurity = document.getElementById('nav-item-security');
        const navUsers = document.getElementById('nav-item-users');
        const navAudit = document.getElementById('nav-item-audit');
        const btnAdvanceClock = document.getElementById('advance-clock-btn');
        const btnCleanReset = document.getElementById('btn-clean-reset');

        if (isPassenger) {
            if (navPassenger) navPassenger.style.display = 'block';
            if (navDashboard) navDashboard.style.display = 'none';
            if (navBaggage) navBaggage.style.display = 'none';
            if (navPassengers) navPassengers.style.display = 'none';
            if (navFlights) navFlights.style.display = 'none';
            if (navConveyors) navConveyors.style.display = 'none';
            if (navSecurity) navSecurity.style.display = 'none';
            if (navUsers) navUsers.style.display = 'none';
            if (navAudit) navAudit.style.display = 'none';
            if (btnAdvanceClock) btnAdvanceClock.style.display = 'none';
            if (btnCleanReset) btnCleanReset.style.display = 'none';

            // Populate passenger banner details
            document.getElementById('passenger-banner-name').innerText = `Welcome, ${state.passengerName}`;
            document.getElementById('passenger-banner-id').innerText = state.linkedPassengerId || 'P-SELF';
        } else {
            if (navPassenger) navPassenger.style.display = 'none';
            if (navDashboard) navDashboard.style.display = 'block';
            if (navBaggage) navBaggage.style.display = 'block';
            if (navPassengers) navPassengers.style.display = 'block';
            if (navFlights) navFlights.style.display = 'block';
            if (navConveyors) navConveyors.style.display = 'block';
            if (navSecurity) navSecurity.style.display = canClear ? 'block' : 'none';
            if (navUsers) navUsers.style.display = isAdmin ? 'block' : 'none';
            if (navAudit) navAudit.style.display = isAdmin ? 'block' : 'none';
            if (btnAdvanceClock) btnAdvanceClock.style.display = 'inline-flex';
            if (btnCleanReset) btnCleanReset.style.display = isAdmin ? 'inline-flex' : 'none';
        }
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

        if (state.currentRole === 'PASSENGER') {
            switchView('passenger-portal');
        } else {
            switchView('dashboard');
        }
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

        // Demo login pills
        document.querySelectorAll('.demo-pill').forEach(btn => {
            btn.addEventListener('click', () => {
                const user = btn.getAttribute('data-user');
                const pass = btn.getAttribute('data-pass');
                document.getElementById('username').value = user;
                document.getElementById('password').value = pass;
                handleLogin();
            });
        });

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
                setCurrentUser(data);
                document.getElementById('password').value = '';
                showDashboard();
                showToast(`Welcome back, ${data.passengerName || data.username}!`, 'success');
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
            loginBtn.querySelector('.btn-text').innerText = 'Sign In to Vaanam Portal';
        }
    }

    async function handleRegister() {
        const username = document.getElementById('reg-username').value.trim();
        const password = document.getElementById('reg-password').value;
        const confirm = document.getElementById('reg-confirm').value;
        const role = document.getElementById('reg-role').value;
        const clearance = document.getElementById('reg-clearance').checked;
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
                body: JSON.stringify({ username, password, role, clearanceAuthority: clearance })
            });

            const data = await res.json();
            if (res.ok) {
                succDiv.innerText = `Account created successfully for ${username}! You may now sign in.`;
                succDiv.style.display = 'block';
                document.getElementById('reg-username').value = '';
                document.getElementById('reg-password').value = '';
                document.getElementById('reg-confirm').value = '';
            } else {
                errDiv.innerText = data.error || 'Failed to create user account.';
                errDiv.style.display = 'block';
            }
        } catch (e) {
            errDiv.innerText = 'Error connecting to server.';
            errDiv.style.display = 'block';
        }
    }

    // ==================== NAVIGATION ====================

    function setupNavigation() {
        document.querySelectorAll('.sidebar-nav .nav-item').forEach(link => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                const targetView = link.getAttribute('data-target');
                if (targetView) switchView(targetView);
            });
        });

        // Dashboard quick links
        const linkFlights = document.getElementById('dash-view-all-flights');
        if (linkFlights) linkFlights.addEventListener('click', () => switchView('flights'));

        // Clean Reset button
        const btnReset = document.getElementById('btn-clean-reset');
        if (btnReset) {
            btnReset.addEventListener('click', async () => {
                if (confirm('Clean Database Reset:\n\nThis will reinitialize the database with the pristine 4-flight, 12-passenger, 20-bag demo dataset. Proceed?')) {
                    try {
                        const res = await fetch('/api/system/reset', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ confirm: true })
                        });
                        const data = await res.json();
                        if (res.ok) {
                            showToast(data.status, 'success');
                            loadAllData();
                        } else {
                            showToast(data.error, 'error');
                        }
                    } catch (e) {
                        showToast('Error resetting database', 'error');
                    }
                }
            });
        }
    }

    function switchView(viewName) {
        state.currentView = viewName;

        // Update active nav link
        document.querySelectorAll('.sidebar-nav .nav-item').forEach(link => {
            if (link.getAttribute('data-target') === viewName) {
                link.classList.add('active');
            } else {
                link.classList.remove('active');
            }
        });

        // Hide all views & show active
        document.querySelectorAll('.content-view').forEach(v => v.classList.remove('active'));
        const targetSection = document.getElementById(`view-${viewName}`);
        if (targetSection) targetSection.classList.add('active');

        // Update top heading
        const headings = {
            'dashboard': { title: 'Operations Dashboard', sub: 'Real-Time Flight Cutoff & Baggage Flow Monitoring' },
            'passenger-portal': { title: 'Passenger Baggage Tracking', sub: 'Self-Service Baggage Status & Route Checkpoint Progress' },
            'baggage': { title: 'Baggage Operations', sub: 'Two-Way Handling, Priority Routing & Node Dispatch' },
            'passengers': { title: 'Passenger Manifest', sub: 'Authorized Passenger Directory & Associated Baggage' },
            'flights': { title: 'Flights & Loading Bays', sub: 'Departure Cutoff Times & Aircraft Bay Assignments' },
            'conveyors': { title: 'Conveyor Network', sub: 'Weighted Directed Graph Topology & Live Fail-Safe Rerouting' },
            'security': { title: 'Security Screening Hub', sub: 'Screening Clearance Decisions & Hold Baggage Inspection' },
            'users': { title: 'User Account Management', sub: 'System Roles, Credentials & Certified Clearance Permissions' },
            'audit': { title: 'Operational Audit Trail', sub: 'Immutable Event History & State Mutation Records' }
        };

        if (headings[viewName]) {
            pageHeading.innerText = headings[viewName].title;
            pageSubheading.innerText = headings[viewName].sub;
        }
    }

    // ==================== DATA LOADING ====================

    async function loadAllData() {
        await Promise.all([
            loadStats(),
            loadBaggage(),
            loadFlights(),
            loadBays(),
            loadConveyors(),
            loadTime()
        ]);

        if (state.currentRole === 'ADMIN' || state.currentRole === 'BAGGAGE_MANAGER') {
            loadPassengers();
        }
        if (state.currentRole === 'ADMIN') {
            loadUsers();
            loadAudit();
        }
    }

    async function loadStats() {
        try {
            const res = await fetch('/api/stats');
            if (res.ok) {
                const stats = await res.json();
                document.getElementById('dash-stat-total').innerText = stats.total || 0;
                document.getElementById('dash-stat-outgoing').innerText = stats.outgoingCount || 0;
                document.getElementById('dash-stat-incoming').innerText = stats.incomingCount || 0;
                document.getElementById('dash-stat-clearance').innerText = stats.awaitingClearance || 0;
                document.getElementById('dash-stat-queued').innerText = (stats.queued || 0) + (stats.inTransit || 0);
                document.getElementById('dash-stat-intransit').innerText = stats.inTransit || 0;
                document.getElementById('dash-stat-loaded').innerText = (stats.loaded || 0) + (stats.collected || 0);
                document.getElementById('dash-stat-collected').innerText = stats.collected || 0;
                document.getElementById('dash-stat-missed').innerText = stats.missedCutoff || 0;
                document.getElementById('dash-stat-conveyors').innerText = `${stats.availableConveyors || 0} / ${stats.totalConveyors || 0}`;
                if (stats.formattedTime) simulatedClockTime.innerText = stats.formattedTime;
            }
        } catch (e) {
            console.error('Error loading stats', e);
        }
    }

    async function loadBaggage() {
        try {
            let url = '/api/baggage';
            if (state.searchQuery) url += `?q=${encodeURIComponent(state.searchQuery)}`;
            const res = await fetch(url);
            if (res.ok) {
                state.baggageList = await res.json();
                renderBaggageTable();
                renderSecurityTable();
                renderAttentionTable();

                if (state.currentRole === 'PASSENGER') {
                    renderPassengerBags();
                }
            }
        } catch (e) {
            console.error('Error loading baggage', e);
        }
    }

    async function loadPassengers() {
        try {
            const res = await fetch('/api/passengers');
            if (res.ok) {
                state.passengerList = await res.json();
                renderPassengersTable();
                populatePassengerSelects();
            }
        } catch (e) {
            console.error('Error loading passengers', e);
        }
    }

    async function loadFlights() {
        try {
            const res = await fetch('/api/flights');
            if (res.ok) {
                state.flightList = await res.json();
                renderFlightsTables();
                populateFlightSelects();
            }
        } catch (e) {
            console.error('Error loading flights', e);
        }
    }

    async function loadBays() {
        try {
            const res = await fetch('/api/bays');
            if (res.ok) {
                state.bayList = await res.json();
                renderBaysGrid();
            }
        } catch (e) {
            console.error('Error loading bays', e);
        }
    }

    async function loadConveyors() {
        try {
            const res = await fetch('/api/conveyors');
            if (res.ok) {
                state.conveyorList = await res.json();
                renderConveyorsTable();
            }
        } catch (e) {
            console.error('Error loading conveyors', e);
        }
    }

    async function loadUsers() {
        try {
            const res = await fetch('/api/users');
            if (res.ok) {
                const users = await res.json();
                renderUsersTable(users);
            }
        } catch (e) {
            console.error('Error loading users', e);
        }
    }

    async function loadAudit() {
        try {
            const res = await fetch('/api/audit');
            if (res.ok) {
                const logs = await res.json();
                renderAuditTable(logs);
            }
        } catch (e) {
            console.error('Error loading audit logs', e);
        }
    }

    async function loadTime() {
        try {
            const res = await fetch('/api/time');
            if (res.ok) {
                const data = await res.json();
                if (data.formattedTime) simulatedClockTime.innerText = data.formattedTime;
            }
        } catch (e) {}
    }

    // ==================== RENDERING LOGIC ====================

    // Helper: Status Badges
    function getStatusBadge(status) {
        const s = (status || '').toUpperCase();
        let badgeClass = 'badge-primary';
        if (s.includes('LOADED') || s.includes('COLLECTED') || s.includes('CLEARED')) badgeClass = 'badge-success';
        else if (s.includes('AWAITING') || s.includes('QUEUED')) badgeClass = 'badge-warning';
        else if (s.includes('MISSED') || s.includes('REJECTED') || s.includes('EXCEPTION')) badgeClass = 'badge-danger';
        else if (s.includes('IN_TRANSIT') || s.includes('ROUTING')) badgeClass = 'badge-primary';
        return `<span class="badge ${badgeClass}">${status}</span>`;
    }

    // Render Baggage Table
    function renderBaggageTable() {
        const tbody = document.getElementById('baggage-table-body');
        if (!tbody) return;

        let filtered = state.baggageList;
        if (state.bagFilter !== 'ALL') {
            if (state.bagFilter === 'CLEARANCE') {
                filtered = filtered.filter(b => b.status === 'AWAITING_CLEARANCE');
            } else if (state.bagFilter === 'LOADED') {
                filtered = filtered.filter(b => b.status === 'LOADED' || b.status === 'COLLECTED');
            } else if (state.bagFilter === 'EXCEPTION') {
                filtered = filtered.filter(b => b.status === 'MISSED_CUTOFF' || b.status === 'EXCEPTION' || b.status === 'REJECTED');
            } else {
                filtered = filtered.filter(b => b.direction === state.bagFilter || b.status === state.bagFilter);
            }
        }

        if (filtered.length === 0) {
            tbody.innerHTML = `<tr><td colspan="11" style="text-align:center; padding: 2rem;" class="subtext">No baggage records found matching criteria.</td></tr>`;
            return;
        }

        const canOperate = (state.currentRole === 'ADMIN' || state.currentRole === 'BAGGAGE_MANAGER');
        const canScreen = state.clearanceAuthority;

        tbody.innerHTML = filtered.map(b => {
            let actionsHtml = `<button class="btn btn-sm btn-outline btn-track-bag" data-id="${b.bagId}">🔍 Track</button>`;
            
            if (canOperate) {
                if (b.status === 'QUEUED' || b.status === 'IN_TRANSIT') {
                    actionsHtml += ` <button class="btn btn-sm btn-primary btn-step-bag" data-id="${b.bagId}" title="Step to next conveyor node">👟 Step</button>`;
                }
                if (b.status === 'QUEUED') {
                    actionsHtml += ` <button class="btn btn-sm btn-success btn-process-bag" data-id="${b.bagId}" title="Process entire route">▶ Run</button>`;
                }
            }

            if (canScreen && b.status === 'AWAITING_CLEARANCE') {
                actionsHtml += ` <button class="btn btn-sm btn-warning btn-screen-bag" data-id="${b.bagId}">🛡️ Screen</button>`;
            }

            return `
                <tr>
                    <td><strong>${b.bagId}</strong></td>
                    <td>
                        <div>${b.passengerName}</div>
                        <span class="subtext">${b.passengerId}</span>
                    </td>
                    <td><span class="badge badge-muted">${b.direction}</span></td>
                    <td>${b.category}</td>
                    <td>${b.flightId || 'UNASSIGNED'}</td>
                    <td><code>${b.currentLocation}</code></td>
                    <td><code>${b.destinationNode || '-'}</code></td>
                    <td>${b.cutoff}</td>
                    <td>${b.cleared ? '<span style="color:#10b981;">✓ Cleared</span>' : '<span style="color:#f59e0b;">⏳ Pending</span>'}</td>
                    <td>${getStatusBadge(b.status)}</td>
                    <td>${actionsHtml}</td>
                </tr>
            `;
        }).join('');

        // Attach action handlers
        tbody.querySelectorAll('.btn-track-bag').forEach(btn => {
            btn.addEventListener('click', () => openTrackingModal(btn.getAttribute('data-id')));
        });
        tbody.querySelectorAll('.btn-step-bag').forEach(btn => {
            btn.addEventListener('click', () => stepBaggageNode(btn.getAttribute('data-id')));
        });
        tbody.querySelectorAll('.btn-process-bag').forEach(btn => {
            btn.addEventListener('click', () => processBaggageDirect(btn.getAttribute('data-id')));
        });
        tbody.querySelectorAll('.btn-screen-bag').forEach(btn => {
            btn.addEventListener('click', () => openClearanceModal(btn.getAttribute('data-id')));
        });
    }

    // Render Passenger Personal Bags Grid (PASSENGER role)
    function renderPassengerBags() {
        const container = document.getElementById('passenger-bags-container');
        if (!container) return;

        if (state.baggageList.length === 0) {
            container.innerHTML = `<div class="panel" style="grid-column: 1/-1; text-align: center; padding: 2rem;">
                <p class="subtext">No baggage checked under your passenger ID (${state.linkedPassengerId}).</p>
            </div>`;
            return;
        }

        container.innerHTML = state.baggageList.map(b => `
            <div class="passenger-bag-card">
                <div>
                    <div class="bag-card-top">
                        <span class="bag-tag-num">${b.bagId}</span>
                        <span class="bag-flight-badge">${b.flightId || 'Arrival'}</span>
                    </div>
                    <div class="bag-card-body">
                        <div class="bag-detail-row">
                            <span>Status:</span>
                            <span>${getStatusBadge(b.status)}</span>
                        </div>
                        <div class="bag-detail-row">
                            <span>Current Position:</span>
                            <span><code>${b.currentLocation}</code></span>
                        </div>
                        <div class="bag-detail-row">
                            <span>Destination:</span>
                            <span><code>${b.destinationNode || '-'}</code></span>
                        </div>
                        <div class="bag-detail-row">
                            <span>Journey:</span>
                            <span>${b.originAirport} → ${b.destinationAirport}</span>
                        </div>
                        <div class="bag-detail-row">
                            <span>Screening:</span>
                            <span>${b.cleared ? '✅ Verified' : '⏳ Awaiting'}</span>
                        </div>
                    </div>
                </div>
                <button class="btn btn-outline btn-block btn-passenger-track" data-id="${b.bagId}">
                    🔍 Track Route &amp; ETA
                </button>
            </div>
        `).join('');

        container.querySelectorAll('.btn-passenger-track').forEach(btn => {
            btn.addEventListener('click', () => {
                const bagId = btn.getAttribute('data-id');
                renderPassengerLiveRoute(bagId);
            });
        });

        // Automatically display first bag route if available
        if (state.baggageList.length > 0 && !state.activeTrackedBagId) {
            renderPassengerLiveRoute(state.baggageList[0].bagId);
        }
    }

    // Render Passenger Live Tracking Embedded Pathway
    async function renderPassengerLiveRoute(bagId) {
        state.activeTrackedBagId = bagId;
        const panel = document.getElementById('passenger-live-tracking-panel');
        if (!panel) return;
        panel.style.display = 'block';

        try {
            const res = await fetch(`/api/baggage/track?id=${encodeURIComponent(bagId)}`);
            if (res.ok) {
                const b = await res.json();
                document.getElementById('passenger-active-bag-label').innerText = `Tag: ${b.bagId} (${b.passengerName})`;
                document.getElementById('passenger-live-status-badge').innerHTML = getStatusBadge(b.status);
                document.getElementById('pass-track-bag-id').innerText = b.bagId;
                document.getElementById('pass-track-flight').innerText = b.flightId || 'Arrival';
                document.getElementById('pass-track-airports').innerText = `${b.originAirport} → ${b.destinationAirport}`;
                document.getElementById('pass-track-location').innerText = b.currentLocation;
                document.getElementById('pass-track-dest').innerText = b.destinationNode || '-';
                document.getElementById('pass-track-eta').innerText = (b.estimatedTime >= 0) ? `${b.estimatedTime} Mins` : 'N/A';

                renderVisualPathway('passenger-visual-route', b);
            }
        } catch (e) {
            console.error('Error fetching passenger route', e);
        }
    }

    // Render Security Clearance Table
    function renderSecurityTable() {
        const tbody = document.getElementById('security-table-body');
        if (!tbody) return;

        const awaiting = state.baggageList.filter(b => b.status === 'AWAITING_CLEARANCE');
        if (awaiting.length === 0) {
            tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; padding: 2rem;" class="subtext">All baggage currently cleared through security screening.</td></tr>`;
            return;
        }

        tbody.innerHTML = awaiting.map(b => `
            <tr>
                <td><strong>${b.bagId}</strong></td>
                <td>${b.passengerName} (${b.passengerId})</td>
                <td>${b.category}</td>
                <td>${b.flightId}</td>
                <td><code>${b.currentLocation}</code></td>
                <td>${getStatusBadge(b.status)}</td>
                <td>
                    <button class="btn btn-sm btn-success btn-clear-approve" data-id="${b.bagId}">Approve</button>
                    <button class="btn btn-sm btn-danger btn-clear-reject" data-id="${b.bagId}">Reject</button>
                </td>
            </tr>
        `).join('');

        tbody.querySelectorAll('.btn-clear-approve').forEach(btn => {
            btn.addEventListener('click', () => submitClearance(btn.getAttribute('data-id'), true, 'Screening inspection passed'));
        });
        tbody.querySelectorAll('.btn-clear-reject').forEach(btn => {
            btn.addEventListener('click', () => openClearanceModal(btn.getAttribute('data-id')));
        });
    }

    // Render Attention Table (Dashboard)
    function renderAttentionTable() {
        const tbody = document.getElementById('dash-attention-body');
        if (!tbody) return;

        const attentionBags = state.baggageList.filter(b => 
            b.status === 'AWAITING_CLEARANCE' || b.status === 'MISSED_CUTOFF' || b.status === 'EXCEPTION' || b.status === 'REJECTED'
        ).slice(0, 6);

        if (attentionBags.length === 0) {
            tbody.innerHTML = `<tr><td colspan="5" style="text-align:center; padding: 1.5rem;" class="subtext">No operational exceptions or screening backlogs.</td></tr>`;
            return;
        }

        tbody.innerHTML = attentionBags.map(b => `
            <tr>
                <td><strong>${b.bagId}</strong></td>
                <td>${b.passengerName}</td>
                <td>${b.flightId || 'N/A'}</td>
                <td><code>${b.currentLocation}</code></td>
                <td>${getStatusBadge(b.status)}</td>
            </tr>
        `).join('');
    }

    // Render Passengers Manifest Table
    function renderPassengersTable() {
        const tbody = document.getElementById('passengers-table-body');
        if (!tbody) return;

        tbody.innerHTML = state.passengerList.map(p => {
            const count = state.baggageList.filter(b => b.passengerId === p.passengerId).length;
            return `
                <tr>
                    <td><strong>${p.passengerId}</strong></td>
                    <td><strong>${p.fullName}</strong></td>
                    <td>${p.username ? `<code>${p.username}</code>` : '<span class="subtext">None</span>'}</td>
                    <td>${p.flightId || '-'}</td>
                    <td>${p.category}</td>
                    <td>${p.contactInfo || '-'}</td>
                    <td><span class="badge badge-primary">${count} Bags</span></td>
                </tr>
            `;
        }).join('');
    }

    // Render Flights Tables
    function renderFlightsTables() {
        const tbodyFull = document.getElementById('flights-full-body');
        const tbodyDash = document.querySelector('#dash-flights-table tbody');

        const rows = state.flightList.map(f => `
            <tr>
                <td><strong>${f.flightId}</strong></td>
                <td>${f.destination}</td>
                <td>${f.formattedDeparture || '-'}</td>
                <td><strong>${f.formattedCutoff}</strong></td>
                <td><code>${f.assignedBay || 'NONE'}</code></td>
                <td>
                    <button class="btn btn-sm btn-outline btn-edit-flight" data-id="${f.flightId}" data-cutoff="${f.cutoff}" data-bay="${f.assignedBay}">
                        ✏️ Edit Cutoff/Bay
                    </button>
                </td>
            </tr>
        `).join('');

        if (tbodyFull) {
            tbodyFull.innerHTML = rows;
            tbodyFull.querySelectorAll('.btn-edit-flight').forEach(btn => {
                btn.addEventListener('click', () => openFlightModal(btn));
            });
        }

        if (tbodyDash) {
            tbodyDash.innerHTML = state.flightList.map(f => `
                <tr>
                    <td><strong>${f.flightId}</strong></td>
                    <td>${f.destination}</td>
                    <td>${f.formattedDeparture || '-'}</td>
                    <td>${f.formattedCutoff}</td>
                    <td><code>${f.assignedBay}</code></td>
                </tr>
            `).join('');
        }
    }

    // Render Loading Bays Grid
    function renderBaysGrid() {
        const container = document.getElementById('bays-container');
        if (!container) return;

        container.innerHTML = state.bayList.map(bay => {
            const assigned = bay.assignedFlight && bay.assignedFlight !== 'NONE';
            return `
                <div class="bay-card">
                    <div class="bay-id">${bay.bayId}</div>
                    <div class="bay-flight">${assigned ? `✈ Flight ${bay.assignedFlight}` : '<span class="subtext">Available (Empty)</span>'}</div>
                </div>
            `;
        }).join('');
    }

    // Render Conveyors Table
    function renderConveyorsTable() {
        const tbody = document.getElementById('conveyors-table-body');
        if (!tbody) return;

        tbody.innerHTML = state.conveyorList.map(c => `
            <tr>
                <td><code>${c.fromNode}</code></td>
                <td><code>${c.toNode}</code></td>
                <td>${c.processingTime} mins</td>
                <td>${c.available ? '<span class="badge badge-success">Operational (Online)</span>' : '<span class="badge badge-danger">Mechanical Jam (Offline)</span>'}</td>
                <td>
                    <button class="btn btn-sm ${c.available ? 'btn-danger' : 'btn-success'} btn-toggle-conveyor"
                        data-from="${c.fromNode}" data-to="${c.toNode}" data-avail="${c.available}">
                        ${c.available ? '⚠️ Trigger Failure' : '🔄 Restore Online'}
                    </button>
                </td>
            </tr>
        `).join('');

        tbody.querySelectorAll('.btn-toggle-conveyor').forEach(btn => {
            btn.addEventListener('click', async () => {
                const from = btn.getAttribute('data-from');
                const to = btn.getAttribute('data-to');
                const currentAvail = btn.getAttribute('data-avail') === 'true';
                const newAvail = !currentAvail;

                try {
                    const res = await fetch('/api/conveyors/toggle', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ fromNode: from, toNode: to, available: newAvail })
                    });
                    if (res.ok) {
                        showToast(`Conveyor ${from} → ${to} marked ${newAvail ? 'Online' : 'Offline (Rerouted)'}`, 'success');
                        loadConveyors();
                        loadBaggage();
                        loadStats();
                    } else {
                        const err = await res.json();
                        showToast(err.error || 'Failed to toggle conveyor', 'error');
                    }
                } catch (e) {
                    showToast('Connection error', 'error');
                }
            });
        });
    }

    // Render Users Table (ADMIN)
    function renderUsersTable(users) {
        const tbody = document.getElementById('users-table-body');
        if (!tbody) return;

        tbody.innerHTML = users.map(u => `
            <tr>
                <td><strong>${u.username}</strong></td>
                <td><span class="badge badge-primary">${u.role}</span></td>
                <td>${u.clearanceAuthority ? '<span style="color:#10b981;">✓ Certified</span>' : '<span class="subtext">No</span>'}</td>
                <td>${u.linkedPassengerId ? `<code>${u.linkedPassengerId}</code>` : '<span class="subtext">-</span>'}</td>
                <td>${u.active ? '<span class="badge badge-success">Active</span>' : '<span class="badge badge-danger">Deactivated</span>'}</td>
                <td>
                    <button class="btn btn-sm ${u.active ? 'btn-danger' : 'btn-success'} btn-toggle-user" data-user="${u.username}" data-active="${u.active}">
                        ${u.active ? 'Deactivate' : 'Activate'}
                    </button>
                </td>
            </tr>
        `).join('');

        tbody.querySelectorAll('.btn-toggle-user').forEach(btn => {
            btn.addEventListener('click', async () => {
                const user = btn.getAttribute('data-user');
                const active = btn.getAttribute('data-active') === 'true';
                try {
                    const res = await fetch('/api/users/update', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ username: user, action: 'status', value: String(!active) })
                    });
                    const data = await res.json();
                    if (res.ok) {
                        showToast(`User ${user} updated`, 'success');
                        loadUsers();
                    } else {
                        showToast(data.error, 'error');
                    }
                } catch (e) {
                    showToast('Error updating user', 'error');
                }
            });
        });
    }

    // Render Audit Table (ADMIN)
    function renderAuditTable(logs) {
        const tbody = document.getElementById('audit-full-body');
        if (!tbody) return;

        tbody.innerHTML = logs.map(l => `
            <tr>
                <td>#${l.logId}</td>
                <td style="font-size:0.78rem;">${l.timestamp}</td>
                <td><strong>${l.username}</strong></td>
                <td><code>${l.action}</code></td>
                <td>${l.targetRecord || '-'}</td>
                <td>${l.simulatedTime}m</td>
                <td>${l.result === 'SUCCESS' ? '<span class="badge badge-success">SUCCESS</span>' : '<span class="badge badge-danger">FAILED</span>'}</td>
                <td class="subtext">${l.reason || '-'}</td>
            </tr>
        `).join('');
    }

    // ==================== VISUAL ROUTE PATHWAY BUILDER ====================

    function renderVisualPathway(containerId, bag) {
        const container = document.getElementById(containerId);
        if (!container) return;

        const route = bag.route || [];
        const current = bag.currentLocation;
        const dest = bag.destinationNode;

        if (route.length === 0) {
            container.innerHTML = `<div style="padding:1rem; text-align:center;" class="subtext">
                Current Location: <code>${current}</code> • Destination: <code>${dest || 'None'}</code> (No route active)
            </div>`;
            return;
        }

        let currentIndex = route.indexOf(current);
        if (currentIndex === -1 && current) {
            // Bag is at start or intermediate
            currentIndex = 0;
        }

        let html = '';
        for (let i = 0; i < route.length; i++) {
            const node = route[i];
            let stateClass = '';

            if (i < currentIndex) stateClass = 'cleared';
            else if (i === currentIndex) stateClass = 'current';
            else if (i === route.length - 1) stateClass = 'destination';
            else stateClass = 'upcoming';

            let icon = '📦';
            if (node.includes('BAY')) icon = '🛫';
            else if (node.includes('CAROUSEL')) icon = '🎠';
            else if (node.includes('TRANSFER')) icon = '🔄';
            else if (node.includes('CHECK-IN')) icon = '🎫';
            else if (node.includes('UNLOAD')) icon = '🛬';

            html += `
                <div class="route-node ${stateClass}">
                    <div class="node-bubble">${icon}</div>
                    <span class="node-name">${node}</span>
                </div>
            `;

            if (i < route.length - 1) {
                html += `<div class="route-arrow">➔</div>`;
            }
        }

        container.innerHTML = html;
    }

    // Open Tracking Modal (Staff & Full view)
    async function openTrackingModal(bagId) {
        const modal = document.getElementById('modal-tracking');
        if (!modal) return;
        modal.style.display = 'flex';

        try {
            const res = await fetch(`/api/baggage/track?id=${encodeURIComponent(bagId)}`);
            if (res.ok) {
                const b = await res.json();
                document.getElementById('track-bag-id').innerText = b.bagId;
                document.getElementById('track-passenger-name').innerText = `${b.passengerName} (${b.passengerId})`;
                document.getElementById('track-bag-type').innerText = `${b.direction} • ${b.originAirport} → ${b.destinationAirport}`;
                document.getElementById('track-flight-id').innerText = b.flightId || 'N/A';
                document.getElementById('track-status-badge').innerHTML = getStatusBadge(b.status);
                document.getElementById('track-eta').innerText = (b.estimatedTime >= 0) ? `${b.estimatedTime} Mins (Arrival ~ ${b.formattedSimulatedTime})` : 'N/A';
                document.getElementById('track-cutoff').innerText = b.cutoff || 'N/A';

                renderVisualPathway('visual-route-path', b);

                // History table
                const histBody = document.getElementById('track-history-body');
                if (histBody) {
                    const history = b.history || [];
                    if (history.length === 0) {
                        histBody.innerHTML = `<tr><td colspan="6" class="subtext" style="text-align:center;">No movement recorded yet.</td></tr>`;
                    } else {
                        histBody.innerHTML = history.map((h, idx) => `
                            <tr>
                                <td>${idx + 1}</td>
                                <td><code>${h.from}</code></td>
                                <td><code>${h.to}</code></td>
                                <td>${getStatusBadge(h.status)}</td>
                                <td>${h.time}m</td>
                                <td class="subtext">${h.timestamp}</td>
                            </tr>
                        `).join('');
                    }
                }
            }
        } catch (e) {
            console.error('Error tracking bag', e);
        }
    }

    // Modal Close Listeners
    document.getElementById('modal-tracking-close').addEventListener('click', () => {
        document.getElementById('modal-tracking').style.display = 'none';
    });
    document.getElementById('modal-tracking-btn-close').addEventListener('click', () => {
        document.getElementById('modal-tracking').style.display = 'none';
    });

    // ==================== BAGGAGE ACTIONS ====================

    function setupBaggageListeners() {
        // Filters
        document.querySelectorAll('.filter-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                document.querySelectorAll('.filter-tab').forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                state.bagFilter = tab.getAttribute('data-filter');
                renderBaggageTable();
            });
        });

        // Search Input
        const searchInput = document.getElementById('baggage-search-input');
        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                state.searchQuery = e.target.value.trim();
                renderBaggageTable();
            });
        }

        const refreshBtn = document.getElementById('baggage-refresh-btn');
        if (refreshBtn) refreshBtn.addEventListener('click', () => loadBaggage());

        // Process Next Bag
        const btnProcessNext = document.getElementById('btn-process-next-bag');
        if (btnProcessNext) {
            btnProcessNext.addEventListener('click', async () => {
                try {
                    const res = await fetch('/api/baggage/process', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({})
                    });
                    const data = await res.json();
                    if (res.ok) {
                        if (data.status === 'Queue is empty') {
                            showToast('Priority Queue is empty.', 'info');
                        } else {
                            showToast(`Processed ${data.bagId} to ${data.currentLocation} (${data.bagStatus})`, 'success');
                            loadBaggage();
                            loadStats();
                        }
                    } else {
                        showToast(data.error || 'Processing failed', 'error');
                    }
                } catch (e) {
                    showToast('Connection error', 'error');
                }
            });
        }

        // Open Register Bag Modal
        const btnOpenReg = document.getElementById('btn-open-register-bag');
        if (btnOpenReg) {
            btnOpenReg.addEventListener('click', () => {
                document.getElementById('modal-register-bag').style.display = 'flex';
                document.getElementById('register-bag-error').style.display = 'none';
            });
        }

        // Direction dropdown toggles destination
        const dirSelect = document.getElementById('new-bag-direction');
        if (dirSelect) {
            dirSelect.addEventListener('change', () => {
                const isIncoming = (dirSelect.value === 'INCOMING');
                document.getElementById('group-flight-select').style.display = isIncoming ? 'none' : 'block';
                document.getElementById('group-dest-select').style.display = isIncoming ? 'block' : 'none';
            });
        }

        // Submit Register Bag
        document.getElementById('modal-reg-bag-submit').addEventListener('click', async () => {
            const bagId = document.getElementById('new-bag-id').value.trim();
            const passengerId = document.getElementById('new-bag-passenger').value;
            const direction = document.getElementById('new-bag-direction').value;
            const categoryRank = document.getElementById('new-bag-category').value;
            const flightId = document.getElementById('new-bag-flight').value;
            const destNode = document.getElementById('new-bag-dest').value;
            const errDiv = document.getElementById('register-bag-error');

            errDiv.style.display = 'none';
            if (!bagId || !passengerId) {
                errDiv.innerText = 'Bag ID and Passenger selection are required.';
                errDiv.style.display = 'block';
                return;
            }

            try {
                const res = await fetch('/api/baggage/register', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        bagId,
                        passengerId,
                        direction,
                        categoryRank,
                        flightId: direction === 'INCOMING' ? '' : flightId,
                        destinationNode: direction === 'INCOMING' ? destNode : ''
                    })
                });
                const data = await res.json();
                if (res.ok) {
                    showToast(`Baggage ${bagId} registered successfully!`, 'success');
                    document.getElementById('modal-register-bag').style.display = 'none';
                    document.getElementById('new-bag-id').value = '';
                    loadBaggage();
                    loadStats();
                } else {
                    errDiv.innerText = data.error || 'Failed to register baggage.';
                    errDiv.style.display = 'block';
                }
            } catch (e) {
                errDiv.innerText = 'Connection error.';
                errDiv.style.display = 'block';
            }
        });

        document.getElementById('modal-reg-bag-close').addEventListener('click', () => {
            document.getElementById('modal-register-bag').style.display = 'none';
        });
        document.getElementById('modal-reg-bag-cancel').addEventListener('click', () => {
            document.getElementById('modal-register-bag').style.display = 'none';
        });
    }

    async function stepBaggageNode(bagId) {
        try {
            const res = await fetch('/api/baggage/step', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ bagId })
            });
            const data = await res.json();
            if (res.ok) {
                showToast(`Bag ${bagId} advanced to ${data.currentLocation} (${data.bagStatus})`, 'success');
                loadBaggage();
                loadStats();
            } else {
                showToast(data.error || 'Failed to step node', 'error');
            }
        } catch (e) {
            showToast('Connection error', 'error');
        }
    }

    async function processBaggageDirect(bagId) {
        try {
            const res = await fetch('/api/baggage/process', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ bagId })
            });
            const data = await res.json();
            if (res.ok) {
                showToast(`Bag ${bagId} routed to ${data.currentLocation} (${data.bagStatus})`, 'success');
                loadBaggage();
                loadStats();
            } else {
                showToast(data.error || 'Processing failed', 'error');
            }
        } catch (e) {
            showToast('Connection error', 'error');
        }
    }

    // ==================== SECURITY CLEARANCE DECISION ====================

    function setupSecurityListeners() {
        document.getElementById('modal-clearance-close').addEventListener('click', () => {
            document.getElementById('modal-clearance-decision').style.display = 'none';
        });
        document.getElementById('modal-clearance-cancel').addEventListener('click', () => {
            document.getElementById('modal-clearance-decision').style.display = 'none';
        });

        document.getElementById('modal-clearance-approve').addEventListener('click', () => {
            const bagId = document.getElementById('clearance-bag-id-display').innerText;
            const reason = document.getElementById('clearance-notes-input').value.trim();
            submitClearance(bagId, true, reason || 'Security screening passed');
        });

        document.getElementById('modal-clearance-reject').addEventListener('click', () => {
            const bagId = document.getElementById('clearance-bag-id-display').innerText;
            const reason = document.getElementById('clearance-notes-input').value.trim();
            if (!reason) {
                alert('A mandatory screening failure reason must be provided when rejecting clearance.');
                return;
            }
            submitClearance(bagId, false, reason);
        });
    }

    function openClearanceModal(bagId) {
        document.getElementById('clearance-bag-id-display').innerText = bagId;
        document.getElementById('clearance-notes-input').value = '';
        document.getElementById('modal-clearance-decision').style.display = 'flex';
    }

    async function submitClearance(bagId, approved, reason) {
        try {
            const res = await fetch('/api/baggage/clearance', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ bagId, approved, reason })
            });
            const data = await res.json();
            if (res.ok) {
                showToast(`Clearance ${approved ? 'Approved' : 'Rejected'} for ${bagId}`, approved ? 'success' : 'error');
                document.getElementById('modal-clearance-decision').style.display = 'none';
                loadBaggage();
                loadStats();
            } else {
                showToast(data.error || 'Clearance submission failed', 'error');
            }
        } catch (e) {
            showToast('Connection error', 'error');
        }
    }

    // ==================== PASSENGER MANAGEMENT & PORTAL ====================

    function setupPassengerListeners() {
        const btnOpenAddP = document.getElementById('btn-open-add-passenger');
        if (btnOpenAddP) {
            btnOpenAddP.addEventListener('click', () => {
                document.getElementById('modal-add-passenger').style.display = 'flex';
                document.getElementById('add-passenger-error').style.display = 'none';
            });
        }

        document.getElementById('modal-add-p-close').addEventListener('click', () => {
            document.getElementById('modal-add-passenger').style.display = 'none';
        });
        document.getElementById('modal-add-p-cancel').addEventListener('click', () => {
            document.getElementById('modal-add-passenger').style.display = 'none';
        });

        document.getElementById('modal-add-p-submit').addEventListener('click', async () => {
            const passengerId = document.getElementById('new-p-id').value.trim();
            const fullName = document.getElementById('new-p-name').value.trim();
            const flightId = document.getElementById('new-p-flight').value;
            const categoryRank = document.getElementById('new-p-class').value;
            const contactInfo = document.getElementById('new-p-contact').value.trim();
            const errDiv = document.getElementById('add-passenger-error');

            errDiv.style.display = 'none';
            if (!passengerId || !fullName) {
                errDiv.innerText = 'Passenger ID and Full Name are required.';
                errDiv.style.display = 'block';
                return;
            }

            try {
                const res = await fetch('/api/passengers', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ passengerId, fullName, flightId, categoryRank, contactInfo })
                });
                const data = await res.json();
                if (res.ok) {
                    showToast(`Passenger ${fullName} created successfully`, 'success');
                    document.getElementById('modal-add-passenger').style.display = 'none';
                    loadPassengers();
                } else {
                    errDiv.innerText = data.error || 'Failed to create passenger.';
                    errDiv.style.display = 'block';
                }
            } catch (e) {
                errDiv.innerText = 'Connection error.';
                errDiv.style.display = 'block';
            }
        });

        // Passenger Quick Track Button (Passenger View)
        const trackBtn = document.getElementById('passenger-track-btn');
        if (trackBtn) {
            trackBtn.addEventListener('click', () => {
                const input = document.getElementById('passenger-track-input').value.trim();
                if (input) renderPassengerLiveRoute(input);
            });
        }

        const refreshBagsBtn = document.getElementById('passenger-refresh-bags-btn');
        if (refreshBagsBtn) refreshBagsBtn.addEventListener('click', () => loadBaggage());
    }

    function populatePassengerSelects() {
        const select = document.getElementById('new-bag-passenger');
        if (select) {
            select.innerHTML = state.passengerList.map(p => 
                `<option value="${p.passengerId}">${p.fullName} (${p.passengerId} • Flight ${p.flightId || 'N/A'})</option>`
            ).join('');
        }
    }

    // ==================== FLIGHT MODAL ====================

    function setupFlightListeners() {
        document.getElementById('modal-flight-close').addEventListener('click', () => {
            document.getElementById('modal-update-flight').style.display = 'none';
        });
        document.getElementById('modal-flight-cancel').addEventListener('click', () => {
            document.getElementById('modal-update-flight').style.display = 'none';
        });

        document.getElementById('modal-flight-submit').addEventListener('click', async () => {
            const flightId = document.getElementById('update-flight-id').value;
            const cutoff = document.getElementById('update-flight-cutoff').value;
            const assignedBay = document.getElementById('update-flight-bay').value;

            try {
                const res = await fetch('/api/flights/update', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ flightId, cutoff, assignedBay })
                });
                const data = await res.json();
                if (res.ok) {
                    showToast(`Flight ${flightId} updated successfully`, 'success');
                    document.getElementById('modal-update-flight').style.display = 'none';
                    loadFlights();
                    loadBays();
                    loadBaggage();
                } else {
                    showToast(data.error || 'Failed to update flight', 'error');
                }
            } catch (e) {
                showToast('Connection error', 'error');
            }
        });
    }

    function openFlightModal(btn) {
        document.getElementById('update-flight-id').value = btn.getAttribute('data-id');
        document.getElementById('update-flight-cutoff').value = btn.getAttribute('data-cutoff');
        document.getElementById('update-flight-bay').value = btn.getAttribute('data-bay') || 'BAY-A';
        document.getElementById('modal-update-flight').style.display = 'flex';
    }

    function populateFlightSelects() {
        const flightOptions = state.flightList.map(f => 
            `<option value="${f.flightId}">${f.flightId} → ${f.destination} (Cutoff: ${f.formattedCutoff})</option>`
        ).join('');

        const newBagFlight = document.getElementById('new-bag-flight');
        if (newBagFlight) newBagFlight.innerHTML = flightOptions;

        const newPFlight = document.getElementById('new-p-flight');
        if (newPFlight) newPFlight.innerHTML = flightOptions;
    }

    function setupConveyorListeners() {}

    // ==================== USER MANAGEMENT (ADMIN) ====================

    function setupUserManagementListeners() {
        const btnOpenAddUser = document.getElementById('btn-admin-add-user');
        if (btnOpenAddUser) {
            btnOpenAddUser.addEventListener('click', () => {
                document.getElementById('modal-create-user').style.display = 'flex';
                document.getElementById('create-user-error').style.display = 'none';

                // Populate passenger dropdown for user linking
                const passSelect = document.getElementById('new-user-passenger-id');
                if (passSelect) {
                    passSelect.innerHTML = `<option value="">None</option>` + state.passengerList.map(p =>
                        `<option value="${p.passengerId}">${p.fullName} (${p.passengerId})</option>`
                    ).join('');
                }
            });
        }

        const roleSelect = document.getElementById('new-user-role');
        if (roleSelect) {
            roleSelect.addEventListener('change', () => {
                const isPassenger = (roleSelect.value === 'PASSENGER');
                document.getElementById('group-new-user-passenger').style.display = isPassenger ? 'block' : 'none';
            });
        }

        document.getElementById('modal-user-close').addEventListener('click', () => {
            document.getElementById('modal-create-user').style.display = 'none';
        });
        document.getElementById('modal-user-cancel').addEventListener('click', () => {
            document.getElementById('modal-create-user').style.display = 'none';
        });

        document.getElementById('modal-user-submit').addEventListener('click', async () => {
            const username = document.getElementById('new-user-name').value.trim();
            const password = document.getElementById('new-user-pass').value;
            const role = document.getElementById('new-user-role').value;
            const clearance = document.getElementById('new-user-clearance').checked;
            const linkedPassengerId = document.getElementById('new-user-passenger-id').value;
            const errDiv = document.getElementById('create-user-error');

            errDiv.style.display = 'none';
            if (!username || !password) {
                errDiv.innerText = 'Username and password are required.';
                errDiv.style.display = 'block';
                return;
            }

            try {
                const res = await fetch('/api/register', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username, password, role, clearanceAuthority: clearance, linkedPassengerId })
                });
                const data = await res.json();
                if (res.ok) {
                    showToast(`User ${username} created!`, 'success');
                    document.getElementById('modal-create-user').style.display = 'none';
                    loadUsers();
                } else {
                    errDiv.innerText = data.error || 'Failed to create user.';
                    errDiv.style.display = 'block';
                }
            } catch (e) {
                errDiv.innerText = 'Connection error.';
                errDiv.style.display = 'block';
            }
        });
    }

    // ==================== SIMULATED CLOCK ====================

    function setupSimulatedClock() {
        const btnAdvance = document.getElementById('advance-clock-btn');
        if (btnAdvance) {
            btnAdvance.addEventListener('click', async () => {
                try {
                    const res = await fetch('/api/time/advance', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ minutes: 15 })
                    });
                    const data = await res.json();
                    if (res.ok) {
                        simulatedClockTime.innerText = data.formattedTime;
                        showToast(`Clock advanced to ${data.formattedTime}`, 'info');
                        loadStats();
                        loadBaggage();
                    }
                } catch (e) {
                    showToast('Failed to advance clock', 'error');
                }
            });
        }
    }

    // ==================== TOAST NOTIFICATIONS ====================

    function showToast(message, type = 'info') {
        const container = document.getElementById('toast-container');
        if (!container) return;

        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        
        let icon = 'ℹ️';
        if (type === 'success') icon = '✅';
        if (type === 'error') icon = '⚠️';

        toast.innerHTML = `<span>${icon}</span> <span>${message}</span>`;
        container.appendChild(toast);

        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(100%)';
            toast.style.transition = 'all 0.3s ease-out';
            setTimeout(() => toast.remove(), 300);
        }, 3500);
    }
});
