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
        activeTrackedBagId: null,
        trackingMode: 'ALL',
        trackingSelectedBagId: null,
        pollingActive: true,
        pollingTimer: null,
        liveTrackingData: { baggage: [], conveyors: [] },
        liveManifestFilter: { query: '', direction: 'ALL', status: 'ALL' },
        graphState: {
            'live-conveyor-svg': {
                panX: 0,
                panY: 0,
                scale: 1.0,
                spacingFactor: 1.0,
                customNodePositions: {},
                initialized: false
            },
            'passenger-conveyor-svg': {
                panX: 0,
                panY: 0,
                scale: 1.0,
                spacingFactor: 1.0,
                customNodePositions: {},
                initialized: false
            }
        }
    };

    // DOM Elements
    const loginScreen = document.getElementById('login-screen');
    const registerScreen = document.getElementById('register-screen');
    const dashboardScreen = document.getElementById('dashboard-screen');
    const pageHeading = document.getElementById('page-heading');
    const pageSubheading = document.getElementById('page-subheading');
    const simulatedClockTime = document.getElementById('simulated-clock-time');

    // ==================== INITIALIZATION ====================

    initTheme();
    checkExistingSession();
    setupAuthListeners();
    setupNavigation();
    setupLiveTracking();
    setupBaggageListeners();
    setupPassengerListeners();
    setupFlightListeners();
    setupSecurityListeners();
    setupUserManagementListeners();
    setupSimulatedClock();

    // ==================== THEME MANAGEMENT ====================

    function initTheme() {
        const savedTheme = localStorage.getItem('vaanam_theme') || 
            (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
        applyTheme(savedTheme);

        const themeToggleBtn = document.getElementById('theme-toggle-btn');
        if (themeToggleBtn) {
            themeToggleBtn.addEventListener('click', () => {
                const current = document.documentElement.getAttribute('data-theme') || 'light';
                const next = current === 'dark' ? 'light' : 'dark';
                applyTheme(next);
            });
        }
    }

    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('vaanam_theme', theme);
        const icon = document.getElementById('theme-toggle-icon');
        const text = document.getElementById('theme-toggle-text');
        if (icon) icon.innerText = theme === 'dark' ? '☀️' : '🌙';
        if (text) text.innerText = theme === 'dark' ? 'Light' : 'Dark';

        // Re-render graphs so SVG colors adapt immediately
        if (state.currentView === 'tracking') {
            renderLiveConveyorGraph();
        } else if (state.currentView === 'passenger-portal' && state.activeTrackedBagId) {
            renderPassengerLiveRoute(state.activeTrackedBagId, true);
        }
    }

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
        state.passengerFlight = data.flightId || '';

        document.getElementById('current-user-name').innerText = state.passengerName || state.currentUser;
        document.getElementById('current-user-role').innerText = state.currentRole;
        document.getElementById('avatar-initials').innerText = (state.currentUser || 'US').substring(0, 2).toUpperCase();

        // Configure role-based UI permissions & navigation visibility
        const isPassenger = (state.currentRole === 'PASSENGER');
        const isAdmin = (state.currentRole === 'ADMIN');
        const isManager = (state.currentRole === 'BAGGAGE_MANAGER');
        const canClear = state.clearanceAuthority;

        // Group navigation containers
        const passNavSection = document.getElementById('passenger-nav-section');
        const staffNavSection = document.getElementById('staff-nav-section');
        if (passNavSection) passNavSection.style.display = isPassenger ? 'block' : 'none';
        if (staffNavSection) staffNavSection.style.display = isPassenger ? 'none' : 'block';

        // Navigation elements
        const navPassenger = document.getElementById('nav-item-passenger');
        const navTracking = document.getElementById('nav-item-tracking');
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
            if (navTracking) navTracking.style.display = 'none';
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
            const flightEl = document.getElementById('passenger-banner-flight');
            if (flightEl && state.passengerFlight) {
                flightEl.innerText = state.passengerFlight;
            }
        } else {
            if (navPassenger) navPassenger.style.display = 'none';
            if (navTracking) navTracking.style.display = 'block';
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
        stopLivePolling();
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
        startLivePolling();
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
            stopLivePolling();
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
            'tracking': { title: 'Live Baggage Tracking', sub: 'Node-Based Conveyor Network & Real-Time SQLite Baggage Manifest' },
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

        if (viewName === 'tracking') {
            pollLiveTracking();
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
                renderBaggageFlowDistribution(stats);
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
                renderPriorityQueue();
                renderSecurityTable();
                renderAttentionTable();
                renderFlightLoadingProgress();

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
                renderFlightLoadingProgress();
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

    // Fuzzy urgency badge. Ineligible (incoming/terminal) bags show a dash.
    function getUrgencyBadge(b) {
        if (!b || b.urgencyEligible === false || b.urgencyScore === undefined || b.urgencyScore === null) {
            return '<span class="subtext">—</span>';
        }
        const score = Number(b.urgencyScore) || 0;
        const label = b.urgencyLabel || '';
        let badgeClass = 'badge-success';       // LOW
        if (score >= 85) badgeClass = 'badge-danger';      // CRITICAL
        else if (score >= 65) badgeClass = 'badge-warning'; // HIGH
        else if (score >= 35) badgeClass = 'badge-primary'; // MEDIUM

        const tip = `${b.urgencyRule || ''} | Remaining ${b.urgencyTimeRemaining ?? '-'}m, Travel ${b.urgencyTravelTime ?? '-'}m, Wait ${b.urgencyDelay ?? '-'}m, Transfer ${b.urgencyTransfer ?? '-'}`;
        return `<span class="badge ${badgeClass}" title="${tip.replace(/"/g, '&quot;')}">${score} ${label}</span>`;
    }

    function getUrgencyDetail(b) {
        if (!b || b.urgencyEligible === false) {
            return '<span class="subtext">Not applicable for this incoming/terminal bag</span>';
        }
        return `${getUrgencyBadge(b)} <span class="subtext" style="margin-left:0.35rem;">${b.urgencyRule || ''}</span>`;
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
            tbody.innerHTML = `<tr><td colspan="12" style="text-align:center; padding: 2rem;" class="subtext">No baggage records found matching criteria.</td></tr>`;
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
                    <td>${getUrgencyBadge(b)}</td>
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

    // Render Priority Processing Queue (Baggage Operations View)
    function renderPriorityQueue() {
        const tbody = document.getElementById('priority-queue-body');
        if (!tbody) return;

        const queuedBags = (state.baggageList || []).filter(b => b.status === 'QUEUED');

        if (queuedBags.length === 0) {
            tbody.innerHTML = `<tr><td colspan="9" style="text-align:center; padding: 1.5rem;" class="subtext">Conveyor queue is clear. No bags waiting for induction.</td></tr>`;
            return;
        }

        // Sort according to deterministic priority order:
        // 1. Loading cutoff (earliest first)
        // 2. Transfer bag over origin bag
        // 3. Category rank (1: First, 2: Business, 3: Economy)
        // 4. Urgency score (higher first)
        const sorted = [...queuedBags].sort((b1, b2) => {
            const c1 = b1.cutoff && b1.cutoff !== 'N/A' ? parseInt(b1.cutoff.replace(':', ''), 10) : 99999;
            const c2 = b2.cutoff && b2.cutoff !== 'N/A' ? parseInt(b2.cutoff.replace(':', ''), 10) : 99999;
            if (c1 !== c2) return c1 - c2;

            const t1 = (b1.direction === 'TRANSFER');
            const t2 = (b2.direction === 'TRANSFER');
            if (t1 && !t2) return -1;
            if (!t1 && t2) return 1;

            const r1 = b1.category && b1.category.includes('First') ? 1 : (b1.category && b1.category.includes('Business') ? 2 : 3);
            const r2 = b2.category && b2.category.includes('First') ? 1 : (b2.category && b2.category.includes('Business') ? 2 : 3);
            if (r1 !== r2) return r1 - r2;

            return (b2.urgencyScore || 0) - (b1.urgencyScore || 0);
        });

        const canOperate = (state.currentRole === 'ADMIN' || state.currentRole === 'BAGGAGE_MANAGER');

        tbody.innerHTML = sorted.map((b, idx) => {
            let actionsHtml = `<button class="btn btn-sm btn-outline btn-track-bag" data-id="${b.bagId}">🔍 Track</button>`;
            if (canOperate) {
                actionsHtml += ` <button class="btn btn-sm btn-primary btn-step-bag" data-id="${b.bagId}" title="Step to next conveyor node">👟 Step</button>`;
                actionsHtml += ` <button class="btn btn-sm btn-success btn-process-bag" data-id="${b.bagId}" title="Process entire route">▶ Run</button>`;
            }

            return `
                <tr>
                    <td><strong>#${idx + 1}</strong></td>
                    <td><strong>${b.bagId}</strong></td>
                    <td>${b.passengerName} <span class="subtext">(${b.passengerId})</span></td>
                    <td>${b.category}</td>
                    <td><code>${b.flightId || 'TRANSFER'}</code></td>
                    <td><code>${b.destinationNode || '-'}</code></td>
                    <td><strong>${b.cutoff || '-'}</strong></td>
                    <td>${getUrgencyBadge(b)}</td>
                    <td>${actionsHtml}</td>
                </tr>
            `;
        }).join('');

        tbody.querySelectorAll('.btn-track-bag').forEach(btn => {
            btn.addEventListener('click', () => openTrackingModal(btn.getAttribute('data-id')));
        });
        tbody.querySelectorAll('.btn-step-bag').forEach(btn => {
            btn.addEventListener('click', () => stepBaggageNode(btn.getAttribute('data-id')));
        });
        tbody.querySelectorAll('.btn-process-bag').forEach(btn => {
            btn.addEventListener('click', () => processBaggageDirect(btn.getAttribute('data-id')));
        });
    }

    // Render Baggage Status Flow & Distribution Chart
    function renderBaggageFlowDistribution(stats) {
        const bar = document.getElementById('flow-segmented-bar');
        const legend = document.getElementById('flow-legend-grid');
        const totalBadge = document.getElementById('dash-flow-total-badge');
        if (!bar || !legend) return;

        const total = stats.total || (state.baggageList || []).length || 1;
        if (totalBadge) totalBadge.innerText = `${stats.total || 0} Total Bags`;

        const queued = stats.queued || 0;
        const inTransit = stats.inTransit || 0;
        const awaiting = stats.awaitingClearance || 0;
        const loaded = stats.loaded || 0;
        const collected = stats.collected || 0;
        const exceptions = (stats.missedCutoff || 0) + (stats.exceptions || 0);

        const items = [
            { label: 'Queued', count: queued, color: '#2563eb', cls: 'flow-queued' },
            { label: 'In Transit', count: inTransit, color: '#0284c7', cls: 'flow-intransit' },
            { label: 'Awaiting Screening', count: awaiting, color: '#d97706', cls: 'flow-clearance' },
            { label: 'Loaded', count: loaded, color: '#16a34a', cls: 'flow-loaded' },
            { label: 'Claimed / Collected', count: collected, color: '#0d9488', cls: 'flow-collected' },
            { label: 'Missed / Exception', count: exceptions, color: '#dc2626', cls: 'flow-missed' }
        ];

        bar.innerHTML = items.map(it => {
            const pct = total > 0 ? ((it.count / total) * 100).toFixed(1) : 0;
            return `<div class="flow-segment ${it.cls}" style="width: ${pct}%;" title="${it.label}: ${it.count} (${pct}%)"></div>`;
        }).join('');

        legend.innerHTML = items.map(it => {
            const pct = total > 0 ? Math.round((it.count / total) * 100) : 0;
            return `
                <div class="flow-legend-item">
                    <span class="flow-dot" style="background-color: ${it.color};"></span>
                    <span><strong>${it.label}:</strong> ${it.count} <span class="subtext">(${pct}%)</span></span>
                </div>
            `;
        }).join('');
    }

    // Render Flight Loading Readiness & Cutoff Progress Cards
    function renderFlightLoadingProgress() {
        const container = document.getElementById('flight-progress-container');
        if (!container) return;

        if (!state.flightList || state.flightList.length === 0) {
            container.innerHTML = `<div class="subtext" style="padding:1rem; text-align:center;">No active flight schedules found.</div>`;
            return;
        }

        const simTime = state.simulatedTime || 540;

        container.innerHTML = state.flightList.map(f => {
            const assignedBags = (state.baggageList || []).filter(b => b.flightId === f.flightId);
            const totalAssigned = assignedBags.length;
            const loadedCount = assignedBags.filter(b => b.status === 'LOADED').length;
            const pct = totalAssigned > 0 ? Math.round((loadedCount / totalAssigned) * 100) : 0;

            const remaining = f.loadingCutoff - simTime;
            let cutoffBadge = '';
            if (remaining <= 0) {
                cutoffBadge = `<span class="badge badge-danger">Cutoff Passed (${f.formattedCutoff})</span>`;
            } else if (remaining <= 30) {
                cutoffBadge = `<span class="badge badge-warning">Cutoff in ${remaining}m (${f.formattedCutoff})</span>`;
            } else {
                cutoffBadge = `<span class="badge badge-outline">Cutoff: ${f.formattedCutoff} (${remaining}m)</span>`;
            }

            return `
                <div class="flight-progress-card">
                    <div class="flight-progress-header">
                        <span class="flight-code">${f.flightId}</span>
                        <span class="badge badge-primary">${f.assignedBay || 'UNASSIGNED'}</span>
                    </div>
                    <div class="flight-route" title="${f.origin} → ${f.destination}">${f.origin} → ${f.destination}</div>
                    <div class="progress-track">
                        <div class="progress-fill" style="width: ${pct}%;"></div>
                    </div>
                    <div class="flight-progress-footer">
                        <span><strong>${loadedCount}/${totalAssigned}</strong> bags loaded (${pct}%)</span>
                        ${cutoffBadge}
                    </div>
                </div>
            `;
        }).join('');
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

        const activeFlight = state.passengerFlight || (state.baggageList.find(b => b.flightId) || {}).flightId;
        const flightEl = document.getElementById('passenger-banner-flight');
        if (flightEl && activeFlight) {
            flightEl.innerText = activeFlight;
        }

        container.querySelectorAll('.btn-passenger-track').forEach(btn => {
            btn.addEventListener('click', () => {
                const bagId = btn.getAttribute('data-id');
                renderPassengerLiveRoute(bagId, false);
            });
        });

        // Automatically display first bag route if available
        if (state.baggageList.length > 0 && !state.activeTrackedBagId) {
            renderPassengerLiveRoute(state.baggageList[0].bagId, true);
        }
    }

    // Human-readable status mapping for passengers
    function getFriendlyPassengerStatus(status) {
        switch (status) {
            case 'REGISTERED': return 'Baggage Checked-In: Awaiting initial conveyor screening';
            case 'AWAITING_CLEARANCE': return 'Security Inspection: Under mandatory security screening';
            case 'CLEARED': return 'Security Cleared: Verified for conveyor intake';
            case 'QUEUED': return 'In Conveyor Queue: Waiting at sorting entry point';
            case 'IN_TRANSIT': return 'Moving on Conveyor: Routing through airport conveyor network';
            case 'AT_LOADING_BAY': return 'At Bay Area: Reached aircraft departure loading point';
            case 'LOADED': return 'Loaded: Securely stowed onto departing flight';
            case 'MISSED_CUTOFF': return 'Cutoff Missed: Delivery delayed past flight cutoff. Ground staff notified';
            case 'ARRIVAL_PENDING': return 'Inbound Arrival: Flight touched down, awaiting offload';
            case 'UNLOADED': return 'Aircraft Unloaded: Transferring onto terminal conveyor';
            case 'AT_COLLECTION_POINT': return 'Ready for Claim: Arrived at baggage carousel';
            case 'COLLECTED': return 'Claimed: Baggage successfully collected by passenger';
            case 'EXCEPTION': return 'Routing Exception: Network conveyor jammed or segment rerouted';
            case 'REJECTED': return 'Security Alert: Prohibited item detected; baggage held';
            default: return status || 'Processing';
        }
    }

    // Render Passenger Live Tracking Embedded Pathway
    async function renderPassengerLiveRoute(bagId, isBackgroundRefresh = false) {
        const feedbackEl = document.getElementById('passenger-track-feedback');
        const panel = document.getElementById('passenger-live-tracking-panel');
        if (!panel) return;

        if (!bagId || !bagId.trim()) {
            if (feedbackEl && !isBackgroundRefresh) {
                feedbackEl.style.display = 'block';
                feedbackEl.style.backgroundColor = '#fee2e2';
                feedbackEl.style.color = '#991b1b';
                feedbackEl.innerText = 'Please enter a valid Baggage Tag ID (e.g. BAG-101).';
            }
            return;
        }

        const normalizedTag = bagId.trim().toUpperCase();
        state.activeTrackedBagId = normalizedTag;

        try {
            const res = await fetch(`/api/baggage/track?id=${encodeURIComponent(normalizedTag)}`);
            if (res.ok) {
                const b = await res.json();
                if (feedbackEl && !isBackgroundRefresh) {
                    feedbackEl.style.display = 'block';
                    feedbackEl.style.backgroundColor = '#dcfce7';
                    feedbackEl.style.color = '#166534';
                    feedbackEl.innerText = `Found baggage record for ${b.bagId}. Live details updated below.`;
                }

                panel.style.display = 'block';
                document.getElementById('passenger-active-bag-label').innerText = `Tag: ${b.bagId} (${b.passengerName})`;
                document.getElementById('passenger-live-status-badge').innerHTML = getStatusBadge(b.status);
                document.getElementById('pass-track-bag-id').innerText = b.bagId;
                const passNameEl = document.getElementById('pass-track-passenger-name');
                if (passNameEl) passNameEl.innerText = `${b.passengerName} (${b.passengerId})`;

                document.getElementById('pass-track-flight').innerText = b.flightId || 'Inbound';
                document.getElementById('pass-track-airports').innerText = `${b.originAirport} → ${b.destinationAirport}`;
                document.getElementById('pass-track-location').innerText = b.currentLocation;
                document.getElementById('pass-track-dest').innerText = b.destinationNode || '-';

                const screenEl = document.getElementById('pass-track-screening');
                if (screenEl) {
                    screenEl.innerText = b.cleared ? `Cleared (${b.clearanceReason || 'Verified'})` : 'Pending Inspection';
                }

                const friendlyEl = document.getElementById('pass-track-friendly-status');
                if (friendlyEl) {
                    friendlyEl.innerText = getFriendlyPassengerStatus(b.status);
                }

                const etaEl = document.getElementById('pass-track-eta');
                if (etaEl) {
                    if (b.status === 'LOADED' || b.status === 'COLLECTED') {
                        etaEl.innerText = `Completed (At ${b.destinationNode || b.currentLocation})`;
                    } else if (b.status === 'MISSED_CUTOFF') {
                        etaEl.innerText = 'Cutoff Missed - Cutoff was ' + (b.cutoff || 'earlier');
                    } else if (b.estimatedTime >= 0) {
                        const arrTime = b.formattedSimulatedTime ? ` ~ ETA ${b.formattedSimulatedTime}` : '';
                        etaEl.innerText = `${b.estimatedTime} Mins${arrTime}`;
                    } else {
                        etaEl.innerText = 'Route recalculating / unavailable';
                    }
                }

                const updatedEl = document.getElementById('pass-track-updated');
                if (updatedEl) {
                    updatedEl.innerText = b.updatedAt || new Date().toLocaleTimeString();
                }

                renderVisualPathway('passenger-visual-route', b);
                renderConveyorNetworkSvg('passenger-conveyor-svg', state.baggageList, state.conveyorList, b);

                // Scroll smoothly into view only on explicit user request
                if (!isBackgroundRefresh) {
                    panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                }
            } else if (res.status === 404) {
                if (feedbackEl && !isBackgroundRefresh) {
                    feedbackEl.style.display = 'block';
                    feedbackEl.style.backgroundColor = '#fee2e2';
                    feedbackEl.style.color = '#991b1b';
                    feedbackEl.innerText = `No baggage found with this tag ID: ${normalizedTag}`;
                }
            } else if (res.status === 403) {
                if (feedbackEl && !isBackgroundRefresh) {
                    feedbackEl.style.display = 'block';
                    feedbackEl.style.backgroundColor = '#fef3c7';
                    feedbackEl.style.color = '#92400e';
                    feedbackEl.innerText = `Access restricted: Baggage ${normalizedTag} is registered to another passenger account.`;
                }
            } else {
                if (feedbackEl && !isBackgroundRefresh) {
                    feedbackEl.style.display = 'block';
                    feedbackEl.style.backgroundColor = '#fee2e2';
                    feedbackEl.style.color = '#991b1b';
                    feedbackEl.innerText = `Server error while querying baggage ${normalizedTag}.`;
                }
            }
        } catch (e) {
            console.error('Error fetching passenger route', e);
            if (feedbackEl && !isBackgroundRefresh) {
                feedbackEl.style.display = 'block';
                feedbackEl.style.backgroundColor = '#fee2e2';
                feedbackEl.style.color = '#991b1b';
                feedbackEl.innerText = 'Network error: Unable to contact tracking service.';
            }
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
                document.getElementById('track-urgency').innerHTML = getUrgencyDetail(b);

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

        const refreshBtn = document.getElementById('baggage-refresh-btn');
        if (refreshBtn) refreshBtn.addEventListener('click', () => loadBaggage());

        // Process Next Bag (Toolbar)
        const btnProcessNext = document.getElementById('btn-process-next-bag');
        if (btnProcessNext) {
            btnProcessNext.addEventListener('click', triggerProcessNextBag);
        }

        // Process Next in Priority Queue
        const btnProcessPriority = document.getElementById('btn-process-next-priority');
        if (btnProcessPriority) {
            btnProcessPriority.addEventListener('click', triggerProcessNextBag);
        }

        async function triggerProcessNextBag() {
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
                        showToast(`Processed highest priority bag ${data.bagId} to ${data.currentLocation} (${data.bagStatus})`, 'success');
                        loadBaggage();
                        loadStats();
                    }
                } else {
                    showToast(data.error || 'Processing failed', 'error');
                }
            } catch (e) {
                showToast('Connection error', 'error');
            }
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

        // Passenger Quick Track Button & Enter key handler (Passenger View)
        const trackBtn = document.getElementById('passenger-track-btn');
        const trackInput = document.getElementById('passenger-track-input');
        
        function triggerPassengerTrack() {
            if (trackInput) {
                const inputVal = trackInput.value.trim();
                renderPassengerLiveRoute(inputVal);
            }
        }

        if (trackBtn) {
            trackBtn.addEventListener('click', triggerPassengerTrack);
        }
        if (trackInput) {
            trackInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    triggerPassengerTrack();
                }
            });
        }

        const refreshBagsBtn = document.getElementById('passenger-refresh-bags-btn');
        if (refreshBagsBtn) refreshBagsBtn.addEventListener('click', () => loadBaggage());

        setupGraphToolbar('passenger-conveyor-svg');
        setupGraphInteractions('passenger-conveyor-svg', 'passenger-conveyor-graph-wrapper');
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

    // ==================== LIVE BAGGAGE TRACKING & CONVEYOR GRAPH ====================

    const POLLING_INTERVAL_MS = 2500;

    function setupLiveTracking() {
        // Mode Toggles
        const btnModeAll = document.getElementById('btn-mode-all');
        const btnModeSingle = document.getElementById('btn-mode-single');
        const singlePanel = document.getElementById('live-single-bag-panel');
        const allPanel = document.getElementById('live-all-bags-panel');

        if (btnModeAll && btnModeSingle) {
            btnModeAll.addEventListener('click', () => {
                state.trackingMode = 'ALL';
                state.trackingSelectedBagId = null;
                btnModeAll.classList.add('active');
                btnModeSingle.classList.remove('active');
                if (singlePanel) singlePanel.style.display = 'none';
                if (allPanel) allPanel.style.display = 'block';
                const clearBtn = document.getElementById('btn-tracking-clear-search');
                if (clearBtn) clearBtn.style.display = 'none';
                const searchInp = document.getElementById('tracking-quick-search-input');
                if (searchInp) searchInp.value = '';
                renderLiveConveyorGraph();
            });

            btnModeSingle.addEventListener('click', () => {
                state.trackingMode = 'SINGLE';
                btnModeSingle.classList.add('active');
                btnModeAll.classList.remove('active');
                if (!state.trackingSelectedBagId && state.liveTrackingData.baggage.length > 0) {
                    selectBagForTracking(state.liveTrackingData.baggage[0].bagId);
                } else if (state.trackingSelectedBagId) {
                    selectBagForTracking(state.trackingSelectedBagId);
                }
            });
        }

        // Quick Search
        const searchInput = document.getElementById('tracking-quick-search-input');
        const searchBtn = document.getElementById('btn-tracking-quick-search');
        const clearBtn = document.getElementById('btn-tracking-clear-search');

        if (searchBtn && searchInput) {
            const doSearch = () => {
                const query = searchInput.value.trim().toUpperCase();
                if (!query) return;
                const match = state.liveTrackingData.baggage.find(b => b.bagId.toUpperCase() === query);
                if (match) {
                    selectBagForTracking(match.bagId);
                    if (clearBtn) clearBtn.style.display = 'inline-block';
                } else {
                    fetch(`/api/baggage/track?id=${encodeURIComponent(query)}`)
                        .then(r => r.ok ? r.json() : null)
                        .then(b => {
                            if (b && b.bagId) {
                                selectBagForTracking(b.bagId);
                                if (clearBtn) clearBtn.style.display = 'inline-block';
                            } else {
                                showToast(`Baggage ID "${query}" not found in database.`, 'error');
                            }
                        })
                        .catch(() => showToast('Error querying baggage ID', 'error'));
                }
            };

            searchBtn.addEventListener('click', doSearch);
            searchInput.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') doSearch();
            });
        }

        if (clearBtn) {
            clearBtn.addEventListener('click', () => {
                if (searchInput) searchInput.value = '';
                clearBtn.style.display = 'none';
                if (btnModeAll) btnModeAll.click();
            });
        }

        // Poll Controls
        const refreshBtn = document.getElementById('btn-tracking-refresh');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => {
                pollLiveTracking();
                showToast('Polled database successfully', 'info');
            });
        }

        const pollToggleBtn = document.getElementById('btn-tracking-toggle-poll');
        if (pollToggleBtn) {
            pollToggleBtn.addEventListener('click', () => {
                state.pollingActive = !state.pollingActive;
                if (state.pollingActive) {
                    pollToggleBtn.innerText = '⏸️';
                    pollToggleBtn.title = 'Pause live polling';
                    startLivePolling();
                    showToast('Live database polling resumed (2.5s)', 'info');
                } else {
                    pollToggleBtn.innerText = '▶️';
                    pollToggleBtn.title = 'Resume live polling';
                    stopLivePolling();
                    const statusBadge = document.getElementById('tracking-poll-status');
                    if (statusBadge) {
                        statusBadge.className = 'poll-badge paused';
                        statusBadge.innerText = '⏸️ Polling Paused';
                    }
                    showToast('Live database polling paused', 'info');
                }
            });
        }

        // Manifest Filter Controls
        const filterQuery = document.getElementById('live-table-filter-query');
        if (filterQuery) {
            filterQuery.addEventListener('input', (e) => {
                state.liveManifestFilter.query = e.target.value.trim().toLowerCase();
                renderLiveManifestTable();
            });
        }

        const filterDir = document.getElementById('live-table-filter-direction');
        if (filterDir) {
            filterDir.addEventListener('change', (e) => {
                state.liveManifestFilter.direction = e.target.value;
                renderLiveManifestTable();
            });
        }

        const filterStatus = document.getElementById('live-table-filter-status');
        if (filterStatus) {
            filterStatus.addEventListener('change', (e) => {
                state.liveManifestFilter.status = e.target.value;
                renderLiveManifestTable();
            });
        }

        // Mode B Actions
        const closeInspectorBtn = document.getElementById('btn-live-single-close');
        if (closeInspectorBtn && btnModeAll) {
            closeInspectorBtn.addEventListener('click', () => {
                btnModeAll.click();
            });
        }

        const advanceBtn = document.getElementById('btn-live-single-advance');
        if (advanceBtn) {
            advanceBtn.addEventListener('click', async () => {
                if (!state.trackingSelectedBagId) return;
                try {
                    advanceBtn.disabled = true;
                    const res = await fetch('/api/baggage/step', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ bagId: state.trackingSelectedBagId })
                    });
                    const data = await res.json();
                    if (res.ok) {
                        showToast(`Bag ${state.trackingSelectedBagId} moved to ${data.currentLocation} (${data.status})`, 'success');
                        await pollLiveTracking();
                    } else {
                        showToast(data.error || 'Failed to advance baggage', 'error');
                    }
                } catch (e) {
                    showToast('Connection error', 'error');
                } finally {
                    advanceBtn.disabled = false;
                }
            });
        }

        const collectBtn = document.getElementById('btn-live-single-collect');
        if (collectBtn) {
            collectBtn.addEventListener('click', async () => {
                if (!state.trackingSelectedBagId) return;
                try {
                    collectBtn.disabled = true;
                    const res = await fetch('/api/baggage/collect', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ bagId: state.trackingSelectedBagId })
                    });
                    const data = await res.json();
                    if (res.ok) {
                        showToast(`Bag ${state.trackingSelectedBagId} successfully collected!`, 'success');
                        await pollLiveTracking();
                    } else {
                        showToast(data.error || 'Failed to collect baggage', 'error');
                    }
                } catch (e) {
                    showToast('Connection error', 'error');
                } finally {
                    collectBtn.disabled = false;
                }
            });
        }

        // Tab visibility listener
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                stopLivePolling();
            } else if (state.pollingActive && state.currentUser) {
                startLivePolling();
            }
        });

        setupGraphToolbar('live-conveyor-svg');
        setupGraphInteractions('live-conveyor-svg', 'live-conveyor-graph-wrapper');
    }

    function startLivePolling() {
        stopLivePolling();
        pollLiveTracking();
        state.pollingTimer = setInterval(pollLiveTracking, POLLING_INTERVAL_MS);
    }

    function stopLivePolling() {
        if (state.pollingTimer) {
            clearInterval(state.pollingTimer);
            state.pollingTimer = null;
        }
        const topbarBadge = document.getElementById('topbar-poll-badge');
        if (topbarBadge) {
            topbarBadge.innerHTML = '<span class="poll-dot paused"></span><span>Paused</span>';
        }
    }

    async function pollLiveTracking() {
        try {
            const res = await fetch('/api/tracking/live');
            const statusBadge = document.getElementById('tracking-poll-status');
            const topbarBadge = document.getElementById('topbar-poll-badge');

            if (!res.ok) {
                if (statusBadge) {
                    statusBadge.className = 'poll-badge error';
                    statusBadge.innerText = '⚠️ Server Reconnecting';
                }
                if (topbarBadge) {
                    topbarBadge.innerHTML = '<span class="poll-dot offline"></span><span>Reconnecting</span>';
                }
                return;
            }

            const data = await res.json();
            state.liveTrackingData = data;
            state.baggageList = data.baggage || [];
            state.conveyorList = data.conveyors || [];

            // Update status badge & last polled timestamp
            if (statusBadge && state.pollingActive) {
                statusBadge.className = 'poll-badge online';
                statusBadge.innerText = '🟢 Polling Active (2.5s)';
            }
            if (topbarBadge && state.pollingActive) {
                topbarBadge.innerHTML = '<span class="poll-dot"></span><span>Live</span>';
            }
            const lastPolledEl = document.getElementById('tracking-last-polled');
            if (lastPolledEl) {
                const now = new Date();
                lastPolledEl.innerText = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
            }

            const bagsCountBadge = document.getElementById('graph-bags-count-badge');
            if (bagsCountBadge) {
                bagsCountBadge.innerText = `${(data.baggage || []).length} Bags on Network`;
            }

            // Refresh UI depending on active view
            if (state.currentView === 'dashboard') {
                loadStats();
                renderFlightLoadingProgress();
            } else if (state.currentView === 'baggage') {
                renderBaggageTable();
                renderPriorityQueue();
            } else if (state.currentView === 'tracking') {
                renderLiveManifestTable();
                if (state.trackingMode === 'SINGLE' && state.trackingSelectedBagId) {
                    refreshSingleBagInspector(state.trackingSelectedBagId);
                } else {
                    renderLiveConveyorGraph();
                }
            } else if (state.currentView === 'passenger-portal') {
                renderPassengerBags();
                if (state.activeTrackedBagId) {
                    renderPassengerLiveRoute(state.activeTrackedBagId, true);
                }
            }
        } catch (e) {
            const statusBadge = document.getElementById('tracking-poll-status');
            if (statusBadge) {
                statusBadge.className = 'poll-badge error';
                statusBadge.innerText = '⚠️ Network Disconnected';
            }
            const topbarBadge = document.getElementById('topbar-poll-badge');
            if (topbarBadge) {
                topbarBadge.innerHTML = '<span class="poll-dot offline"></span><span>Disconnected</span>';
            }
        }
    }

    async function selectBagForTracking(bagId) {
        state.trackingMode = 'SINGLE';
        state.trackingSelectedBagId = bagId;

        const btnModeSingle = document.getElementById('btn-mode-single');
        const btnModeAll = document.getElementById('btn-mode-all');
        if (btnModeSingle) btnModeSingle.classList.add('active');
        if (btnModeAll) btnModeAll.classList.remove('active');

        const singlePanel = document.getElementById('live-single-bag-panel');
        if (singlePanel) singlePanel.style.display = 'block';

        const searchInp = document.getElementById('tracking-quick-search-input');
        if (searchInp) searchInp.value = bagId;
        const clearBtn = document.getElementById('btn-tracking-clear-search');
        if (clearBtn) clearBtn.style.display = 'inline-block';

        await refreshSingleBagInspector(bagId);
    }

    async function refreshSingleBagInspector(bagId) {
        try {
            const res = await fetch(`/api/baggage/track?id=${encodeURIComponent(bagId)}`);
            if (!res.ok) return;
            const b = await res.json();

            // Populate Single Bag Info
            const titleEl = document.getElementById('live-single-bag-title');
            if (titleEl) titleEl.innerText = `Baggage Tag: ${b.bagId}`;

            const subEl = document.getElementById('live-single-meta-sub');
            if (subEl) subEl.innerText = `Passenger: ${b.passengerName} (${b.passengerId}) • Flight: ${b.flightId || 'N/A'}`;

            const statusBadge = document.getElementById('live-single-status-badge');
            if (statusBadge) statusBadge.innerHTML = getStatusBadge(b.status);

            const dirBadge = document.getElementById('live-single-direction-badge');
            if (dirBadge) dirBadge.innerText = b.direction;

            document.getElementById('live-single-cur-node').innerText = b.currentLocation || '-';
            document.getElementById('live-single-dest-node').innerText = b.destinationNode || '-';
            document.getElementById('live-single-flight').innerText = b.flightId || 'N/A';
            document.getElementById('live-single-airports').innerText = `${b.originAirport} → ${b.destinationAirport} (${b.direction})`;
            document.getElementById('live-single-eta').innerText = (b.estimatedTime >= 0) ? `${b.estimatedTime} Mins (Calculated by Dijkstra)` : 'N/A';
            document.getElementById('live-single-cutoff').innerText = b.cutoff || 'N/A';
            document.getElementById('live-single-urgency').innerHTML = getUrgencyDetail(b);
            document.getElementById('live-single-clearance').innerText = b.cleared ? '✅ Verified Cleared' : '⏳ Awaiting Screening Clearance';
            document.getElementById('live-single-updated').innerText = b.updatedAt || 'Recent';

            // Show/Hide collect button
            const collectBtn = document.getElementById('btn-live-single-collect');
            if (collectBtn) {
                const canCollect = (b.direction === 'INCOMING' && (b.currentLocation.startsWith('CLAIM-CAROUSEL') || b.status === 'ARRIVED_COLLECTION') && b.status !== 'COLLECTED');
                collectBtn.style.display = canCollect ? 'inline-flex' : 'none';
            }

            // Checkpoints visual route
            renderVisualPathway('live-single-visual-route', b);

            // History table
            const histBody = document.getElementById('live-single-history-body');
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

            // Render Graph with highlighted single bag
            renderConveyorNetworkSvg('live-conveyor-svg', state.liveTrackingData.baggage, state.liveTrackingData.conveyors, b);
        } catch (e) {
            console.error('Error refreshing single bag', e);
        }
    }

    function renderLiveManifestTable() {
        const tbody = document.getElementById('live-tracking-manifest-body');
        if (!tbody) return;

        let list = state.liveTrackingData.baggage || [];
        const { query, direction, status } = state.liveManifestFilter;

        if (query) {
            list = list.filter(b => 
                b.bagId.toLowerCase().includes(query) ||
                b.passengerName.toLowerCase().includes(query) ||
                b.passengerId.toLowerCase().includes(query) ||
                (b.flightId && b.flightId.toLowerCase().includes(query)) ||
                b.currentLocation.toLowerCase().includes(query) ||
                (b.destinationNode && b.destinationNode.toLowerCase().includes(query))
            );
        }

        if (direction !== 'ALL') {
            list = list.filter(b => b.direction === direction);
        }

        if (status !== 'ALL') {
            list = list.filter(b => b.status === status);
        }

        if (list.length === 0) {
            tbody.innerHTML = `<tr><td colspan="12" style="text-align:center; padding: 2rem;" class="subtext">No baggage records match the selected filter.</td></tr>`;
            return;
        }

        tbody.innerHTML = list.map(b => {
            const isSelected = (state.trackingSelectedBagId === b.bagId);
            return `
                <tr class="${isSelected ? 'row-selected' : ''}" style="${isSelected ? 'background-color: #eff6ff;' : ''}">
                    <td><strong>${b.bagId}</strong></td>
                    <td>${b.passengerName} <span class="subtext">(${b.passengerId})</span></td>
                    <td><span class="badge badge-outline">${b.flightId || 'Arrival'}</span></td>
                    <td>${b.direction}</td>
                    <td><code>${b.currentLocation}</code></td>
                    <td><code>${b.destinationNode || '-'}</code></td>
                    <td>${getStatusBadge(b.status)}</td>
                    <td>${b.cleared ? '✅ Cleared' : '⏳ Pending'}</td>
                    <td>${(b.estimatedTime >= 0) ? b.estimatedTime + 'm' : 'N/A'}</td>
                    <td>${getUrgencyBadge(b)}</td>
                    <td class="subtext">${b.updatedAt || '-'}</td>
                    <td>
                        <button class="btn btn-sm btn-primary btn-track-manifest" data-id="${b.bagId}">
                            🎯 Track
                        </button>
                    </td>
                </tr>
            `;
        }).join('');

        tbody.querySelectorAll('.btn-track-manifest').forEach(btn => {
            btn.addEventListener('click', () => {
                const bagId = btn.getAttribute('data-id');
                selectBagForTracking(bagId);
            });
        });
    }

    function renderLiveConveyorGraph() {
        renderConveyorNetworkSvg('live-conveyor-svg', state.liveTrackingData.baggage, state.liveTrackingData.conveyors, null);
    }

    // ==================== TOPOLOGICAL DATABASE GRAPH LAYOUT ====================

    function computeConveyorTopologyLayout(conveyorList, spacingFactor = 1.0, customPositions = {}) {
        conveyorList = conveyorList || (state.conveyorList && state.conveyorList.length > 0 ? state.conveyorList : (state.liveTrackingData && state.liveTrackingData.conveyors ? state.liveTrackingData.conveyors : []));

        // 1. Collect distinct node IDs dynamically from actual conveyor segments
        const nodeSet = new Set();
        (conveyorList || []).forEach(s => {
            if (s.fromNode) nodeSet.add(s.fromNode);
            if (s.toNode) nodeSet.add(s.toNode);
        });

        // Ensure default operational nodes if list is empty
        if (nodeSet.size === 0) {
            ['CHECK-IN', 'UNLOAD-POINT', 'SORTING', 'TRANSFER-B', 'TRANSFER-A', 'CENTRAL', 
             'BAY-AREA', 'BAY-A', 'BAY-B', 'BAY-C', 'BAY-D', 'CLAIM-AREA', 'CLAIM-CAROUSEL-1', 'CLAIM-CAROUSEL-2']
             .forEach(n => nodeSet.add(n));
        }

        // 2. Base layer assignments representing actual airport journey:
        // CHECK-IN / UNLOAD → SORTING → TRANSFER POINTS → CENTRAL → BAY-AREA / CLAIM-AREA → LOADING BAYS / CAROUSELS
        const defaultLayers = {
            'CHECK-IN': 0,
            'UNLOAD-POINT': 0,
            'SORTING': 1,
            'TRANSFER-B': 2,
            'TRANSFER-A': 2,
            'CENTRAL': 3,
            'BAY-AREA': 4,
            'CLAIM-AREA': 4,
            'BAY-A': 5,
            'BAY-B': 5,
            'BAY-C': 5,
            'BAY-D': 5,
            'CLAIM-CAROUSEL-1': 5,
            'CLAIM-CAROUSEL-2': 5
        };

        const layerMap = {};
        nodeSet.forEach(id => {
            if (defaultLayers[id] !== undefined) {
                layerMap[id] = defaultLayers[id];
            } else {
                if (id.startsWith('BAY-') && id !== 'BAY-AREA') layerMap[id] = 5;
                else if (id.includes('CAROUSEL') || id.includes('CLAIM')) layerMap[id] = 5;
                else if (id.includes('TRANSFER')) layerMap[id] = 2;
                else layerMap[id] = 3;
            }
        });

        // 3. Base layout coordinates with generous spacing (1440 x 820 canvas coordinate system)
        const baseXByLayer = {
            0: 120,
            1: 360,
            2: 600,
            3: 840,
            4: 1080,
            5: 1320
        };

        const defaultYPositions = {
            'BAY-A': 90,
            'BAY-B': 200,
            'BAY-C': 310,
            'BAY-D': 420,
            'CLAIM-CAROUSEL-1': 590,
            'CLAIM-CAROUSEL-2': 710,
            'BAY-AREA': 255,
            'CLAIM-AREA': 650,
            'CENTRAL': 370,
            'TRANSFER-B': 150,
            'TRANSFER-A': 320,
            'SORTING': 200,
            'CHECK-IN': 200,
            'UNLOAD-POINT': 560
        };

        const sublabels = {
            'CHECK-IN': 'Departure Origin',
            'UNLOAD-POINT': 'Arrival Aircraft',
            'SORTING': 'Main Sorting Matrix',
            'TRANSFER-B': 'Express Transit',
            'TRANSFER-A': 'Interline Transfer',
            'CENTRAL': 'Main Router Hub',
            'BAY-AREA': 'Departure Manifold',
            'CLAIM-AREA': 'Arrival Claim Router',
            'BAY-A': 'Loading Bay',
            'BAY-B': 'Loading Bay',
            'BAY-C': 'Loading Bay',
            'BAY-D': 'Loading Bay',
            'CLAIM-CAROUSEL-1': 'Domestic Carousel',
            'CLAIM-CAROUSEL-2': 'International Carousel'
        };

        const layout = {};
        const canvasCenterX = 720;
        const canvasCenterY = 410;

        nodeSet.forEach(nodeId => {
            const layer = layerMap[nodeId] !== undefined ? layerMap[nodeId] : 3;
            const rawBaseX = baseXByLayer[layer] !== undefined ? baseXByLayer[layer] : canvasCenterX;
            const rawBaseY = defaultYPositions[nodeId] !== undefined ? defaultYPositions[nodeId] : canvasCenterY;

            // Apply spacing factor around canvas center
            const calculatedX = Math.round(canvasCenterX + (rawBaseX - canvasCenterX) * spacingFactor);
            const calculatedY = Math.round(canvasCenterY + (rawBaseY - canvasCenterY) * spacingFactor);

            // Prioritize user manual drag coordinates if available
            const posX = (customPositions && customPositions[nodeId] && customPositions[nodeId].x !== undefined)
                ? customPositions[nodeId].x
                : calculatedX;
            const posY = (customPositions && customPositions[nodeId] && customPositions[nodeId].y !== undefined)
                ? customPositions[nodeId].y
                : calculatedY;

            let dynamicSub = sublabels[nodeId] || 'Conveyor Node';
            if (nodeId.startsWith('BAY-') && nodeId !== 'BAY-AREA') {
                const assigned = (state.flightList || []).find(f => f.assignedBay === nodeId);
                if (assigned) {
                    dynamicSub = assigned.flightId;
                }
            }

            layout[nodeId] = {
                x: posX,
                y: posY,
                label: nodeId,
                sub: dynamicSub,
                layer: layer
            };
        });

        return layout;
    }

    // ==================== CONVEYOR NETWORK SVG RENDERING ====================

    function renderConveyorNetworkSvg(svgElementId, baggageList, conveyorList, selectedBagDetail) {
        const svg = document.getElementById(svgElementId);
        if (!svg) return;

        baggageList = baggageList || [];
        conveyorList = conveyorList || (state.conveyorList && state.conveyorList.length > 0 ? state.conveyorList : (state.liveTrackingData && state.liveTrackingData.conveyors ? state.liveTrackingData.conveyors : []));

        if (!state.graphState[svgElementId]) {
            state.graphState[svgElementId] = {
                panX: 0,
                panY: 0,
                scale: 1.0,
                spacingFactor: 1.0,
                customNodePositions: {},
                initialized: false
            };
        }
        const gState = state.graphState[svgElementId];

        // Compute dynamic topology layout
        const layout = computeConveyorTopologyLayout(conveyorList, gState.spacingFactor, gState.customNodePositions);

        // Map bags by currentLocation
        const bagsByLoc = {};
        baggageList.forEach(b => {
            const loc = b.currentLocation;
            if (loc) {
                if (!bagsByLoc[loc]) bagsByLoc[loc] = [];
                bagsByLoc[loc].push(b);
            }
        });

        // Determine route segments for selectedBagDetail
        const completedSegments = new Set();
        const remainingSegments = new Set();
        let currentSelectedLoc = null;

        if (selectedBagDetail) {
            currentSelectedLoc = selectedBagDetail.currentLocation;
            const completed = selectedBagDetail.completedRoute || [];
            for (let i = 0; i < completed.length - 1; i++) {
                completedSegments.add(`${completed[i]}->${completed[i + 1]}`);
            }

            const remaining = selectedBagDetail.remainingRoute || selectedBagDetail.route || [];
            for (let i = 0; i < remaining.length - 1; i++) {
                remainingSegments.add(`${remaining[i]}->${remaining[i + 1]}`);
            }
        }

        // Defs with arrow markers
        const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
        const defaultArrowColor = isDark ? '#64748b' : '#94a3b8';
        const routeArrowColor = '#2563eb';
        const completedArrowColor = '#10b981';
        const failedArrowColor = '#ef4444';

        let svgContent = `
            <defs>
                <marker id="arrow-default-${svgElementId}" viewBox="0 0 10 10" refX="7" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                    <path d="M 0 1.5 L 8 5 L 0 8.5 z" fill="${defaultArrowColor}"/>
                </marker>
                <marker id="arrow-route-${svgElementId}" viewBox="0 0 10 10" refX="7" refY="5" markerWidth="6.5" markerHeight="6.5" orient="auto-start-reverse">
                    <path d="M 0 1.5 L 8 5 L 0 8.5 z" fill="${routeArrowColor}"/>
                </marker>
                <marker id="arrow-completed-${svgElementId}" viewBox="0 0 10 10" refX="7" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                    <path d="M 0 1.5 L 8 5 L 0 8.5 z" fill="${completedArrowColor}"/>
                </marker>
                <marker id="arrow-failed-${svgElementId}" viewBox="0 0 10 10" refX="7" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                    <path d="M 0 1.5 L 8 5 L 0 8.5 z" fill="${failedArrowColor}"/>
                </marker>
            </defs>

            <!-- Background Pan Catcher -->
            <rect class="pan-catcher" x="-10000" y="-10000" width="20000" height="20000" fill="transparent"></rect>

            <g id="${svgElementId}-viewport" class="graph-viewport-layer" transform="translate(${gState.panX}, ${gState.panY}) scale(${gState.scale})">
        `;

        // Edges Layer
        svgContent += `<g class="edges-layer">`;
        const R = 24; // Node radius

        conveyorList.forEach(s => {
            const u = layout[s.fromNode];
            const v = layout[s.toNode];
            if (!u || !v) return;

            const segKey = `${s.fromNode}->${s.toNode}`;
            const isFailed = (s.available === false);
            const isRoute = remainingSegments.has(segKey);
            const isCompleted = completedSegments.has(segKey);

            let edgeClass = 'conveyor-edge';
            let markerId = `arrow-default-${svgElementId}`;

            if (isFailed) {
                edgeClass += ' edge-failed';
                markerId = `arrow-failed-${svgElementId}`;
            } else if (isRoute) {
                edgeClass += ' edge-route';
                markerId = `arrow-route-${svgElementId}`;
            } else if (isCompleted) {
                edgeClass += ' edge-route-completed';
                markerId = `arrow-completed-${svgElementId}`;
            }

            // Boundary intersection calculation
            const dx = v.x - u.x;
            const dy = v.y - u.y;
            const dist = Math.sqrt(dx * dx + dy * dy);
            if (dist === 0) return;

            const sx = u.x + (dx / dist) * R;
            const sy = u.y + (dy / dist) * R;
            const ex = v.x - (dx / dist) * (R + 7);
            const ey = v.y - (dy / dist) * (R + 7);

            svgContent += `<path d="M ${sx} ${sy} L ${ex} ${ey}" class="${edgeClass}" marker-end="url(#${markerId})" data-edge="${segKey}"></path>`;

            // Midpoint processing time pill offset perpendicularly
            const mx = (sx + ex) / 2;
            const my = (sy + ey) / 2;
            const normX = -dy / dist;
            const normY = dx / dist;
            const pillX = Math.round(mx + normX * 11);
            const pillY = Math.round(my + normY * 11);

            const timeLabel = isFailed ? 'JAM' : `${s.processingTime}m`;
            const bgFill = isFailed 
                ? (isDark ? '#7f1d1d' : '#fee2e2') 
                : (isRoute ? (isDark ? '#1e3a8a' : '#dbeafe') : (isDark ? '#1e293b' : '#ffffff'));
            const textColor = isFailed 
                ? (isDark ? '#fca5a5' : '#b91c1c') 
                : (isRoute ? (isDark ? '#93c5fd' : '#1d4ed8') : (isDark ? '#e2e8f0' : '#475569'));
            const strokeColor = isFailed
                ? (isDark ? '#991b1b' : '#fca5a5')
                : (isRoute ? (isDark ? '#3b82f6' : '#93c5fd') : (isDark ? '#475569' : '#cbd5e1'));

            svgContent += `
                <g class="edge-weight-group" data-edge="${segKey}" transform="translate(${pillX}, ${pillY})">
                    <rect class="edge-weight-bg" x="-16" y="-9" width="32" height="18" rx="4" fill="${bgFill}" stroke="${strokeColor}"></rect>
                    <text class="edge-weight-text" fill="${textColor}">${timeLabel}</text>
                </g>
            `;
        });
        svgContent += `</g>`;

        // Nodes Layer
        svgContent += `<g class="nodes-layer">`;
        Object.keys(layout).forEach(nodeId => {
            const pos = layout[nodeId];
            const bagsAtNode = bagsByLoc[nodeId] || [];

            let colorClass = 'node-grey';
            if (bagsAtNode.length === 0) {
                if (nodeId.startsWith('TRANSFER')) colorClass = 'node-purple';
            } else {
                if (bagsAtNode.some(b => b.status === 'MISSED_CUTOFF' || b.status === 'REJECTED')) {
                    colorClass = 'node-red';
                } else if (bagsAtNode.some(b => b.status === 'AWAITING_CLEARANCE')) {
                    colorClass = 'node-amber';
                } else if (bagsAtNode.some(b => b.status === 'LOADED' || b.status === 'COLLECTED')) {
                    colorClass = 'node-green';
                } else if (bagsAtNode.some(b => b.direction === 'TRANSFER' || nodeId.startsWith('TRANSFER'))) {
                    colorClass = 'node-purple';
                } else {
                    colorClass = 'node-blue';
                }
            }

            const isSelectedNode = selectedBagDetail && (currentSelectedLoc === nodeId);

            svgContent += `
                <g class="conveyor-node-group" data-node="${nodeId}" title="${nodeId}: ${bagsAtNode.length} bag(s) present (Drag to move, click to inspect)">
                    ${isSelectedNode ? `<circle class="node-pulse-ring" cx="${pos.x}" cy="${pos.y}"></circle>` : ''}
                    <circle class="node-circle ${colorClass} ${isSelectedNode ? 'node-selected' : ''}" cx="${pos.x}" cy="${pos.y}" r="${R}"></circle>
                    <text class="node-label" x="${pos.x}" y="${pos.y - 28}">${pos.label}</text>
                    <text class="node-sublabel" x="${pos.x}" y="${pos.y - 41}">${pos.sub}</text>
            `;

            // Baggage Indicators
            const normalPillFill = isDark ? '#0284c7' : '#0f172a';

            if (bagsAtNode.length === 1) {
                const b = bagsAtNode[0];
                const isBagSelected = selectedBagDetail && (selectedBagDetail.bagId === b.bagId);
                const pillFill = isBagSelected ? '#2563eb' : normalPillFill;
                svgContent += `
                    <g class="node-bag-pill-group" data-bagid="${b.bagId}" title="Track Bag ${b.bagId}">
                        <rect class="node-bag-pill ${isBagSelected ? 'pill-selected' : ''}" x="${pos.x - 35}" y="${pos.y + 26}" width="70" height="18" rx="4" fill="${pillFill}"></rect>
                        <text class="node-bag-pill-text" x="${pos.x}" y="${pos.y + 35}">${b.bagId}</text>
                    </g>
                `;
            } else if (bagsAtNode.length === 2) {
                const b1 = bagsAtNode[0];
                const b2 = bagsAtNode[1];
                const isBag1Selected = selectedBagDetail && (selectedBagDetail.bagId === b1.bagId);
                const isBag2Selected = selectedBagDetail && (selectedBagDetail.bagId === b2.bagId);
                const fill1 = isBag1Selected ? '#2563eb' : normalPillFill;
                const fill2 = isBag2Selected ? '#2563eb' : normalPillFill;
                svgContent += `
                    <g class="node-bag-pill-group" data-bagid="${b1.bagId}" title="Track Bag ${b1.bagId}">
                        <rect class="node-bag-pill ${isBag1Selected ? 'pill-selected' : ''}" x="${pos.x - 35}" y="${pos.y + 26}" width="70" height="17" rx="4" fill="${fill1}"></rect>
                        <text class="node-bag-pill-text" x="${pos.x}" y="${pos.y + 34.5}">${b1.bagId}</text>
                    </g>
                    <g class="node-bag-pill-group" data-bagid="${b2.bagId}" title="Track Bag ${b2.bagId}">
                        <rect class="node-bag-pill ${isBag2Selected ? 'pill-selected' : ''}" x="${pos.x - 35}" y="${pos.y + 45}" width="70" height="17" rx="4" fill="${fill2}"></rect>
                        <text class="node-bag-pill-text" x="${pos.x}" y="${pos.y + 53.5}">${b2.bagId}</text>
                    </g>
                `;
            } else if (bagsAtNode.length > 2) {
                const hasSelected = selectedBagDetail && bagsAtNode.some(b => b.bagId === selectedBagDetail.bagId);
                const multiFill = hasSelected ? '#2563eb' : (isDark ? '#1e293b' : '#e2e8f0');
                const multiTextColor = hasSelected ? '#ffffff' : (isDark ? '#f8fafc' : '#0f172a');
                svgContent += `
                    <g class="node-bag-pill-group pill-multi-group" data-node="${nodeId}" title="Click to view all ${bagsAtNode.length} bags at ${nodeId}">
                        <rect class="node-bag-pill pill-multi ${hasSelected ? 'pill-selected' : ''}" x="${pos.x - 42}" y="${pos.y + 26}" width="84" height="20" rx="4" fill="${multiFill}"></rect>
                        <text class="node-bag-pill-text ${hasSelected ? '' : 'text-multi'}" fill="${multiTextColor}" x="${pos.x}" y="${pos.y + 36}">📦 ${bagsAtNode.length} Bags ▾</text>
                    </g>
                `;
            }

            svgContent += `</g>`;
        });
        svgContent += `</g>`; // End nodes-layer
        svgContent += `</g>`; // End viewport

        svg.innerHTML = svgContent;

        // Attach click listeners to pill groups
        svg.querySelectorAll('.node-bag-pill-group').forEach(grp => {
            grp.addEventListener('click', (e) => {
                e.stopPropagation();
                if (grp.classList.contains('pill-multi-group')) {
                    const nodeId = grp.getAttribute('data-node');
                    const bags = bagsByLoc[nodeId] || [];
                    openNodeBaggageInspector(nodeId, bags);
                } else {
                    const bagId = grp.getAttribute('data-bagid');
                    if (bagId) {
                        if (svgElementId === 'live-conveyor-svg') {
                            selectBagForTracking(bagId);
                        } else if (svgElementId === 'passenger-conveyor-svg') {
                            renderPassengerLiveRoute(bagId, false);
                        }
                    }
                }
            });
        });

        // Initial fit to screen once
        if (!gState.initialized) {
            gState.initialized = true;
            setTimeout(() => fitGraphToScreen(svgElementId), 60);
        }
    }

    // ==================== LIVE NODE AND EDGE REAL-TIME DRAGGING ====================

    function updateLiveNodeAndEdges(svgElementId, nodeId, newX, newY) {
        const svg = document.getElementById(svgElementId);
        if (!svg) return;
        const gState = state.graphState[svgElementId];
        if (!gState) return;

        // 1. Update node element positions
        const nodeGroup = svg.querySelector(`.conveyor-node-group[data-node="${nodeId}"]`);
        if (nodeGroup) {
            const circle = nodeGroup.querySelector('circle.node-circle');
            const pulse = nodeGroup.querySelector('circle.node-pulse-ring');
            const label = nodeGroup.querySelector('text.node-label');
            const sublabel = nodeGroup.querySelector('text.node-sublabel');

            if (circle) { circle.setAttribute('cx', newX); circle.setAttribute('cy', newY); }
            if (pulse) { pulse.setAttribute('cx', newX); pulse.setAttribute('cy', newY); }
            if (label) { label.setAttribute('x', newX); label.setAttribute('y', newY - 28); }
            if (sublabel) { sublabel.setAttribute('x', newX); sublabel.setAttribute('y', newY - 41); }

            const pills = nodeGroup.querySelectorAll('.node-bag-pill-group');
            if (pills.length === 1) {
                const rect = pills[0].querySelector('rect');
                const text = pills[0].querySelector('text');
                if (pills[0].classList.contains('pill-multi-group')) {
                    if (rect) { rect.setAttribute('x', newX - 42); rect.setAttribute('y', newY + 26); }
                    if (text) { text.setAttribute('x', newX); text.setAttribute('y', newY + 36); }
                } else {
                    if (rect) { rect.setAttribute('x', newX - 35); rect.setAttribute('y', newY + 26); }
                    if (text) { text.setAttribute('x', newX); text.setAttribute('y', newY + 35); }
                }
            } else if (pills.length === 2) {
                const rect1 = pills[0].querySelector('rect');
                const text1 = pills[0].querySelector('text');
                const rect2 = pills[1].querySelector('rect');
                const text2 = pills[1].querySelector('text');
                if (rect1) { rect1.setAttribute('x', newX - 35); rect1.setAttribute('y', newY + 26); }
                if (text1) { text1.setAttribute('x', newX); text1.setAttribute('y', newY + 34.5); }
                if (rect2) { rect2.setAttribute('x', newX - 35); rect2.setAttribute('y', newY + 45); }
                if (text2) { text2.setAttribute('x', newX); text2.setAttribute('y', newY + 53.5); }
            }
        }

        // 2. Update connected edges and edge weight badges
        const layout = computeConveyorTopologyLayout(state.conveyorList, gState.spacingFactor, gState.customNodePositions);
        const R = 24;

        const edges = svg.querySelectorAll(`path.conveyor-edge[data-edge^="${nodeId}->"], path.conveyor-edge[data-edge$="->${nodeId}"]`);
        edges.forEach(path => {
            const segKey = path.getAttribute('data-edge');
            const [from, to] = segKey.split('->');
            const u = layout[from];
            const v = layout[to];
            if (!u || !v) return;

            const dx = v.x - u.x;
            const dy = v.y - u.y;
            const dist = Math.sqrt(dx * dx + dy * dy);
            if (dist === 0) return;

            const sx = u.x + (dx / dist) * R;
            const sy = u.y + (dy / dist) * R;
            const ex = v.x - (dx / dist) * (R + 7);
            const ey = v.y - (dy / dist) * (R + 7);

            path.setAttribute('d', `M ${sx} ${sy} L ${ex} ${ey}`);

            const weightGroup = svg.querySelector(`.edge-weight-group[data-edge="${segKey}"]`);
            if (weightGroup) {
                const mx = (sx + ex) / 2;
                const my = (sy + ey) / 2;
                const normX = -dy / dist;
                const normY = dx / dist;
                const pillX = Math.round(mx + normX * 11);
                const pillY = Math.round(my + normY * 11);
                weightGroup.setAttribute('transform', `translate(${pillX}, ${pillY})`);
            }
        });
    }

    // ==================== GRAPH VIEWPORT PAN, ZOOM & DRAG INTERACTIONS ====================

    function setupGraphInteractions(svgElementId, wrapperId) {
        const svg = document.getElementById(svgElementId);
        const wrapper = document.getElementById(wrapperId);
        if (!svg || !wrapper) return;

        if (!state.graphState[svgElementId]) {
            state.graphState[svgElementId] = {
                panX: 0,
                panY: 0,
                scale: 1.0,
                spacingFactor: 1.0,
                customNodePositions: {},
                initialized: false
            };
        }
        const gState = state.graphState[svgElementId];

        let isPanning = false;
        let startMouseX = 0, startMouseY = 0;
        let initialPanX = 0, initialPanY = 0;

        let draggedNodeId = null;
        let dragStartX = 0, dragStartY = 0;
        let hasDragged = false;

        // Mouse Wheel Zoom
        wrapper.addEventListener('wheel', (e) => {
            e.preventDefault();
            const rect = wrapper.getBoundingClientRect();
            const mouseX = e.clientX - rect.left;
            const mouseY = e.clientY - rect.top;

            const factor = e.deltaY < 0 ? 1.15 : 0.85;
            const oldScale = gState.scale;
            const newScale = Math.min(Math.max(parseFloat((oldScale * factor).toFixed(2)), 0.35), 3.0);

            gState.panX = Math.round(mouseX - (mouseX - gState.panX) * (newScale / oldScale));
            gState.panY = Math.round(mouseY - (mouseY - gState.panY) * (newScale / oldScale));
            gState.scale = newScale;

            applyViewportTransform(svgElementId, false);
        }, { passive: false });

        // Pointer Down
        wrapper.addEventListener('pointerdown', (e) => {
            const nodeGroup = e.target.closest('.conveyor-node-group');
            const pillGroup = e.target.closest('.node-bag-pill-group');

            if (pillGroup) return; // Handled by pill group click

            if (nodeGroup) {
                draggedNodeId = nodeGroup.getAttribute('data-node');
                dragStartX = e.clientX;
                dragStartY = e.clientY;
                hasDragged = false;
                nodeGroup.classList.add('is-dragging');
                wrapper.setPointerCapture(e.pointerId);
                return;
            }

            // Canvas Pan
            isPanning = true;
            startMouseX = e.clientX;
            startMouseY = e.clientY;
            initialPanX = gState.panX;
            initialPanY = gState.panY;
            wrapper.classList.add('is-panning');
            wrapper.setPointerCapture(e.pointerId);
        });

        // Pointer Move
        wrapper.addEventListener('pointermove', (e) => {
            if (draggedNodeId) {
                const dx = e.clientX - dragStartX;
                const dy = e.clientY - dragStartY;
                if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
                    hasDragged = true;
                }

                if (hasDragged) {
                    const viewport = document.getElementById(`${svgElementId}-viewport`);
                    if (!viewport) return;

                    const pt = svg.createSVGPoint();
                    pt.x = e.clientX;
                    pt.y = e.clientY;
                    const svgP = pt.matrixTransform(viewport.getScreenCTM().inverse());

                    gState.customNodePositions[draggedNodeId] = {
                        x: Math.round(svgP.x),
                        y: Math.round(svgP.y)
                    };

                    updateLiveNodeAndEdges(svgElementId, draggedNodeId, Math.round(svgP.x), Math.round(svgP.y));
                }
                return;
            }

            if (isPanning) {
                const dx = e.clientX - startMouseX;
                const dy = e.clientY - startMouseY;
                gState.panX = initialPanX + dx;
                gState.panY = initialPanY + dy;
                applyViewportTransform(svgElementId, false);
            }
        });

        // Pointer Up
        const onPointerUp = (e) => {
            if (draggedNodeId) {
                const nodeId = draggedNodeId;
                const nodeGroup = svg.querySelector(`.conveyor-node-group[data-node="${nodeId}"]`);
                if (nodeGroup) nodeGroup.classList.remove('is-dragging');
                draggedNodeId = null;

                if (!hasDragged) {
                    // Click without drag
                    const bags = (state.liveTrackingData && state.liveTrackingData.baggage && state.liveTrackingData.baggage.length > 0 
                        ? state.liveTrackingData.baggage 
                        : state.baggageList).filter(b => b.currentLocation === nodeId);

                    if (bags.length === 1) {
                        if (svgElementId === 'live-conveyor-svg') {
                            selectBagForTracking(bags[0].bagId);
                        } else {
                            renderPassengerLiveRoute(bags[0].bagId, false);
                        }
                    } else if (bags.length > 1) {
                        openNodeBaggageInspector(nodeId, bags);
                    }
                }
            }

            if (isPanning) {
                isPanning = false;
                wrapper.classList.remove('is-panning');
            }
        };

        wrapper.addEventListener('pointerup', onPointerUp);
        wrapper.addEventListener('pointercancel', onPointerUp);
    }

    // ==================== GRAPH TOOLBAR & ARRANGEMENT CONTROLS ====================

    function setupGraphToolbar(svgElementId) {
        const isLive = (svgElementId === 'live-conveyor-svg');
        const prefix = isLive ? 'graph' : 'pax-graph';

        const btnAuto = document.getElementById(`btn-${prefix}-auto-arrange`);
        const btnFit = document.getElementById(`btn-${prefix}-fit`);
        const btnCenter = document.getElementById(`btn-${prefix}-center`);
        const btnReset = document.getElementById(`btn-${prefix}-reset`);
        const btnZoomIn = document.getElementById(`btn-${prefix}-zoom-in`);
        const btnZoomOut = document.getElementById(`btn-${prefix}-zoom-out`);
        const btnSpacingInc = document.getElementById(`btn-${prefix}-spacing-increase`);
        const btnSpacingDec = document.getElementById(`btn-${prefix}-spacing-decrease`);

        if (btnAuto) btnAuto.addEventListener('click', () => autoArrangeGraph(svgElementId));
        if (btnFit) btnFit.addEventListener('click', () => fitGraphToScreen(svgElementId));
        if (btnCenter) btnCenter.addEventListener('click', () => centerGraph(svgElementId));
        if (btnReset) btnReset.addEventListener('click', () => resetGraphLayout(svgElementId));
        if (btnZoomIn) btnZoomIn.addEventListener('click', () => zoomGraph(svgElementId, 1.25));
        if (btnZoomOut) btnZoomOut.addEventListener('click', () => zoomGraph(svgElementId, 0.8));
        if (btnSpacingInc) btnSpacingInc.addEventListener('click', () => adjustGraphSpacing(svgElementId, 0.1));
        if (btnSpacingDec) btnSpacingDec.addEventListener('click', () => adjustGraphSpacing(svgElementId, -0.1));

        // Node Inspector Modal Close Handlers
        const closeInspector = () => {
            const modal = document.getElementById('modal-node-baggage-inspector');
            if (modal) modal.style.display = 'none';
        };
        const closeBtn1 = document.getElementById('modal-node-inspector-close');
        const closeBtn2 = document.getElementById('modal-node-inspector-btn-close');
        if (closeBtn1) closeBtn1.addEventListener('click', closeInspector);
        if (closeBtn2) closeBtn2.addEventListener('click', closeInspector);
    }

    function applyViewportTransform(svgElementId, animate = false) {
        const gState = state.graphState[svgElementId];
        if (!gState) return;
        const viewport = document.getElementById(`${svgElementId}-viewport`);
        if (!viewport) return;

        if (animate) {
            viewport.classList.add('animate-transform');
            setTimeout(() => viewport.classList.remove('animate-transform'), 350);
        } else {
            viewport.classList.remove('animate-transform');
        }

        viewport.setAttribute('transform', `translate(${gState.panX}, ${gState.panY}) scale(${gState.scale})`);

        const zoomEl = document.getElementById(svgElementId === 'live-conveyor-svg' ? 'graph-zoom-level' : 'pax-graph-zoom-level');
        if (zoomEl) zoomEl.innerText = `${Math.round(gState.scale * 100)}%`;
    }

    function fitGraphToScreen(svgElementId) {
        const svg = document.getElementById(svgElementId);
        if (!svg) return;
        const gState = state.graphState[svgElementId];
        if (!gState) return;
        const wrapper = svg.closest('.conveyor-graph-wrapper');
        if (!wrapper) return;

        const layout = computeConveyorTopologyLayout(state.conveyorList, gState.spacingFactor, gState.customNodePositions);
        const nodes = Object.values(layout);
        if (nodes.length === 0) return;

        let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
        nodes.forEach(n => {
            if (n.x < minX) minX = n.x;
            if (n.x > maxX) maxX = n.x;
            if (n.y < minY) minY = n.y;
            if (n.y > maxY) maxY = n.y;
        });

        const margin = 70;
        const graphWidth = (maxX - minX) + margin * 2;
        const graphHeight = (maxY - minY) + margin * 2;
        const wrapperW = wrapper.clientWidth || 1000;
        const wrapperH = wrapper.clientHeight || 560;

        const scaleX = wrapperW / graphWidth;
        const scaleY = wrapperH / graphHeight;
        const fitScale = Math.min(Math.max(Math.min(scaleX, scaleY), 0.4), 1.5);

        const centerX = (minX + maxX) / 2;
        const centerY = (minY + maxY) / 2;

        gState.scale = parseFloat(fitScale.toFixed(2));
        gState.panX = Math.round((wrapperW / 2) - centerX * gState.scale);
        gState.panY = Math.round((wrapperH / 2) - centerY * gState.scale);

        applyViewportTransform(svgElementId, true);
    }

    function centerGraph(svgElementId) {
        const svg = document.getElementById(svgElementId);
        if (!svg) return;
        const gState = state.graphState[svgElementId];
        if (!gState) return;
        const wrapper = svg.closest('.conveyor-graph-wrapper');
        if (!wrapper) return;

        const layout = computeConveyorTopologyLayout(state.conveyorList, gState.spacingFactor, gState.customNodePositions);
        const nodes = Object.values(layout);
        if (nodes.length === 0) return;

        let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
        nodes.forEach(n => {
            if (n.x < minX) minX = n.x;
            if (n.x > maxX) maxX = n.x;
            if (n.y < minY) minY = n.y;
            if (n.y > maxY) maxY = n.y;
        });

        const wrapperW = wrapper.clientWidth || 1000;
        const wrapperH = wrapper.clientHeight || 560;
        const centerX = (minX + maxX) / 2;
        const centerY = (minY + maxY) / 2;

        gState.panX = Math.round((wrapperW / 2) - centerX * gState.scale);
        gState.panY = Math.round((wrapperH / 2) - centerY * gState.scale);

        applyViewportTransform(svgElementId, true);
    }

    function autoArrangeGraph(svgElementId) {
        const gState = state.graphState[svgElementId];
        if (!gState) return;

        gState.customNodePositions = {};
        gState.spacingFactor = 1.0;

        const spacingEl = document.getElementById(svgElementId === 'live-conveyor-svg' ? 'graph-spacing-level' : 'pax-graph-spacing-level');
        if (spacingEl) spacingEl.innerText = '1.0x';

        if (svgElementId === 'live-conveyor-svg') {
            renderLiveConveyorGraph();
        } else if (svgElementId === 'passenger-conveyor-svg') {
            if (state.activeTrackedBagId) renderPassengerLiveRoute(state.activeTrackedBagId, false);
        }

        fitGraphToScreen(svgElementId);
        showToast('Conveyor network layout automatically optimized', 'success');
    }

    function resetGraphLayout(svgElementId) {
        const gState = state.graphState[svgElementId];
        if (!gState) return;

        gState.customNodePositions = {};
        gState.spacingFactor = 1.0;

        const spacingEl = document.getElementById(svgElementId === 'live-conveyor-svg' ? 'graph-spacing-level' : 'pax-graph-spacing-level');
        if (spacingEl) spacingEl.innerText = '1.0x';

        if (svgElementId === 'live-conveyor-svg') {
            renderLiveConveyorGraph();
        } else if (svgElementId === 'passenger-conveyor-svg') {
            if (state.activeTrackedBagId) renderPassengerLiveRoute(state.activeTrackedBagId, false);
        }

        fitGraphToScreen(svgElementId);
        showToast('Conveyor layout and zoom reset to defaults', 'info');
    }

    function zoomGraph(svgElementId, factor) {
        const svg = document.getElementById(svgElementId);
        if (!svg) return;
        const gState = state.graphState[svgElementId];
        if (!gState) return;
        const wrapper = svg.closest('.conveyor-graph-wrapper');
        if (!wrapper) return;

        const wrapperW = wrapper.clientWidth || 1000;
        const wrapperH = wrapper.clientHeight || 560;
        const centerX = wrapperW / 2;
        const centerY = wrapperH / 2;

        const oldScale = gState.scale;
        const newScale = Math.min(Math.max(parseFloat((oldScale * factor).toFixed(2)), 0.35), 3.0);

        gState.panX = Math.round(centerX - (centerX - gState.panX) * (newScale / oldScale));
        gState.panY = Math.round(centerY - (centerY - gState.panY) * (newScale / oldScale));
        gState.scale = newScale;

        applyViewportTransform(svgElementId, true);
    }

    function adjustGraphSpacing(svgElementId, delta) {
        const gState = state.graphState[svgElementId];
        if (!gState) return;

        gState.spacingFactor = Math.min(Math.max(parseFloat((gState.spacingFactor + delta).toFixed(1)), 0.7), 1.6);

        const spacingEl = document.getElementById(svgElementId === 'live-conveyor-svg' ? 'graph-spacing-level' : 'pax-graph-spacing-level');
        if (spacingEl) spacingEl.innerText = `${gState.spacingFactor.toFixed(1)}x`;

        if (svgElementId === 'live-conveyor-svg') {
            renderLiveConveyorGraph();
        } else if (svgElementId === 'passenger-conveyor-svg') {
            if (state.activeTrackedBagId) renderPassengerLiveRoute(state.activeTrackedBagId, false);
        }
    }

    // ==================== NODE BAGGAGE INSPECTOR MODAL ====================

    function openNodeBaggageInspector(nodeId, bags) {
        const modal = document.getElementById('modal-node-baggage-inspector');
        if (!modal) return;

        const titleEl = document.getElementById('modal-node-inspector-title');
        const subtitleEl = document.getElementById('modal-node-inspector-subtitle');
        const tbody = document.getElementById('modal-node-inspector-body');

        if (titleEl) titleEl.innerText = `Node Manifest: ${nodeId}`;
        if (subtitleEl) subtitleEl.innerText = `${bags.length} bag(s) currently positioned at ${nodeId}`;

        if (tbody) {
            tbody.innerHTML = bags.map(b => `
                <tr>
                    <td><strong>${b.bagId}</strong></td>
                    <td>${b.passengerName || 'Passenger'} <span class="subtext">(${b.passengerId || 'P-SELF'})</span></td>
                    <td><span class="badge badge-outline">${b.flightId || 'Arrival'}</span></td>
                    <td><code>${b.destinationNode || 'Carousel'}</code></td>
                    <td>${getStatusBadge(b.status)}</td>
                    <td>
                        <button type="button" class="btn btn-xs btn-primary btn-inspect-track" data-bagid="${b.bagId}">Track Bag</button>
                    </td>
                </tr>
            `).join('');

            tbody.querySelectorAll('.btn-inspect-track').forEach(btn => {
                btn.addEventListener('click', () => {
                    const bagId = btn.getAttribute('data-bagid');
                    modal.style.display = 'none';
                    selectBagForTracking(bagId);
                });
            });
        }

        modal.style.display = 'flex';
    }
});
