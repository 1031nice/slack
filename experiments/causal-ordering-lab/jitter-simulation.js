const SnowflakeId = require('snowflake-id').default;

/**
 * Causal Ordering Simulation (Jitter Lab)
 * 
 * Goal: Prove that Client-Side Reordering fixes sequence gaps caused by network jitter.
 */

// 1. Setup Snowflake Generator
// (In a real distributed system, machineId would differ per server)
const snowflake = new SnowflakeId({
    mid: 1,
    offset: (2026 - 1970) * 31536000 * 1000 // Future offset for 2026
});

// Configuration
const CONFIG = {
    TOTAL_MESSAGES: 1000,
    BASE_LATENCY_MS: 50,
    JITTER_MAX_MS: 100,  // Random delay between 0-100ms
    BUFFER_WINDOW_MS: 200 // Client holding time
};

// Simulation State
const sentMessages = [];
const receivedLog = [];

/**
 * Step 1: Producer
 * Generates messages with strictly increasing Snowflake IDs.
 */
console.log(`[Producer] Generating ${CONFIG.TOTAL_MESSAGES} messages...`);
for (let i = 0; i < CONFIG.TOTAL_MESSAGES; i++) {
    const id = snowflake.generate();
    sentMessages.push({
        seq: i + 1, // Logical sequence for verification
        id: id,
        timestamp: Date.now(),
        content: `Msg ${i + 1}`
    });
    // Tiny delay to ensure timestamps move forward slightly (simulating real chat)
    const start = Date.now();
    while (Date.now() - start < 1) {}
}

/**
 * Step 2: Network (with Jitter)
 * Delivers messages to the client with random delays.
 */
console.log(`[Network] Simulating transmission with 0-${CONFIG.JITTER_MAX_MS}ms jitter...`);

const networkPromises = sentMessages.map(msg => {
    const jitter = Math.floor(Math.random() * CONFIG.JITTER_MAX_MS);
    const totalDelay = CONFIG.BASE_LATENCY_MS + jitter;

    return new Promise(resolve => {
        setTimeout(() => {
            resolve({ msg, arriveTime: Date.now() });
        }, totalDelay);
    });
});

/**
 * Step 3: Consumer (Client)
 * Two strategies: Naive (Immediate) vs. Smart (Reordering Buffer)
 */
Promise.all(networkPromises).then(packets => {
    // Packets arrive in random order due to setTimeout/Jitter
    // Sort by arrival time to simulate the socket stream
    const socketStream = packets.sort((a, b) => a.arriveTime - b.arriveTime);

    console.log(`[Client] Received all ${socketStream.length} packets.`);
    
    // --- Strategy A: Naive (Render as received) ---
    console.log('\n--- Strategy A: Naive (Immediate Render) ---');
    let outOfOrderCount = 0;
    let maxGap = 0;
    let lastId = 0n; // BigInt

    socketStream.forEach(packet => {
        const currentId = BigInt(packet.msg.id);
        if (currentId < lastId) {
            outOfOrderCount++;
        }
        lastId = currentId;
    });

    console.log(`Out-of-Order Messages: ${outOfOrderCount} / ${CONFIG.TOTAL_MESSAGES}`);
    console.log(`Disorder Rate: ${(outOfOrderCount / CONFIG.TOTAL_MESSAGES * 100).toFixed(2)}%`);


    // --- Strategy B: Smart (Client-Side Buffer) ---
    console.log(`\n--- Strategy B: Smart (Buffer Window: ${CONFIG.BUFFER_WINDOW_MS}ms) ---`);
    
    const buffer = [];
    const renderedStream = [];
    let processedCount = 0;
    
    // Simulation of real-time buffering loop
    // Since we have the full trace, we can simulate the timeline
    
    const startTime = socketStream[0].arriveTime;
    const endTime = socketStream[socketStream.length - 1].arriveTime + CONFIG.BUFFER_WINDOW_MS + 1000;

    // We iterate through time in 10ms ticks
    let bufferViolations = 0;
    
    // In a real app, this is "onMessage" and "setInterval"
    // Here we simulate the event loop processing
    
    // We'll reconstruct the flow:
    // 1. Push arrived packets to buffer
    // 2. Check if packet has aged > WINDOW
    // 3. If so, pop from Min-Heap (sort) and render
    
    // Simplified simulation:
    // For each packet, its "Render Time" = Arrival Time + Buffer Window
    // Then we sort by Render Time? No, that's fixed delay.
    
    // Correct logic:
    // At any moment T, we display messages where (Arrival Time + Window) <= T
    // AND we display them in ID order of available messages.
    
    // But wait, standard implementation (Slack) is:
    // "Hold message in buffer. If we have a gap, wait. If timeout, force render."
    // OR "Hold for X ms to see if earlier ID arrives."
    
    // Let's implement the "Hold for Window" strategy:
    // A message is eligible for rendering at (Arrival + Window).
    // When it becomes eligible, we take ALL eligible messages, sort them, and render.
    
    const events = socketStream.map(p => ({
        type: 'ARRIVE',
        time: p.arriveTime,
        packet: p
    }));
    
    // Add "RENDER_READY" events
    socketStream.forEach(p => {
        events.push({
            type: 'READY',
            time: p.arriveTime + CONFIG.BUFFER_WINDOW_MS,
            packet: p
        });
    });
    
    // Sort events by time
    events.sort((a, b) => a.time - b.time);
    
    const pendingBuffer = []; // Unsorted buffer
    const finalRenderLog = [];
    
    events.forEach(event => {
        if (event.type === 'ARRIVE') {
            pendingBuffer.push(event.packet);
        } else if (event.type === 'READY') {
            // Find this packet in buffer and render it, BUT...
            // The key is: When a packet is "Ready", we should also verify if 
            // any *other* packets in the buffer satisfy the order.
            
            // Actually, a simple Reordering Buffer usually works like a Min-Heap.
            // We pop the smallest ID if it's "old enough" OR if we decide to flush.
            
            // Let's simulate the "Strict Window" approach:
            // "I will not show this message until it has sat in the buffer for 200ms."
            // At T = Arrive + 200ms, we insert it into the UI.
            // If a message with SMALLER ID arrived 50ms later (T+50), 
            // its render time is (T+50+200). 
            // So the first message renders at T+200, the smaller one at T+250.
            // RESULT: Still out of order! 
            
            // WAIT! The buffer helps because if:
            // Msg A (ID 100) arrives at T=0.
            // Msg B (ID 90)  arrives at T=50. (Late arrival due to jitter)
            
            // If Naive: Show A(0), Show B(50). -> Disorder.
            
            // If Window=200ms:
            // A is ready at T=200.
            // B is ready at T=250.
            // Still Disorder!
            
            // CORRECT STRATEGY: 
            // We don't just delay. We Sort *within* the buffer.
            // UI Render Loop runs every X ms.
            // It grabs ALL messages that have "arrived".
            // It sorts them.
            // It renders them.
            // BUT, if we rendered ID 100, and then ID 90 arrives... we can't "un-render" (unless we update DOM).
            // So we MUST hold ID 100 until we are reasonably sure ID 90 isn't coming.
            
            // Dynamic Buffer Logic:
            // "Don't render ID N until timestamp(N) < (Now - Window)"
            // This effectively delays ALL messages by Window.
            // Since ID is time-based, ID_A < ID_B means Time_A < Time_B.
            // If we delay rendering by 200ms relative to the ID's timestamp? 
            // No, clocks are skewed.
            
            // Slack's Approach / Standard approach:
            // Render immediately? No.
            // "Soft Ordering": Insert into the DOM at the correct position.
            // If DOM is immutable (append-only log), then we must buffer.
            
            // Let's test the "Insert at correct position" assumption.
            // If the UI allows inserting older messages above newer ones, then Jitter doesn't matter!
            // BUT, "New Message" toast notifications or auto-scroll depends on Append-Only.
            
            // Assumption for this Lab: **Append-Only UI** (Terminal / Simple Log).
            // We cannot insert "historically". We must output in order.
            
            // Re-evaluating Strategy B logic for Append-Only:
            // We hold messages in a Min-Heap.
            // We only pop (render) a message if:
            // 1. It is the smallest ID in the heap.
            // 2. AND it has been in the buffer for > BUFFER_WINDOW_MS.
            
            // Let's trace A(100) at T=0, B(90) at T=50. Window=200.
            // T=0: Heap=[A(100)]. A.age=0.
            // T=50: Heap=[B(90), A(100)]. A.age=50, B.age=0.
            // T=200: A.age=200. Can we pop A?
            //    Top is B(90). B.age=150. Wait.
            // T=250: B.age=200. Pop B(90). Render B.
            //    New Top is A(100). A.age=250. Pop A(100). Render A.
            // Result: B, then A. ORDER RESTORED!
            
            // This is the logic we must implement.
        }
    });

    // Let's execute the "Min-Heap + Age Check" simulation
    
    // We tick every 10ms
    const heap = []; // Simple array, we'll sort on every tick for simplicity
    const finalLog = [];
    
    let currentTime = socketStream[0].arriveTime;
    const endSimTime = socketStream[socketStream.length - 1].arriveTime + CONFIG.BUFFER_WINDOW_MS + 200;
    
    let socketIndex = 0;
    
    while (currentTime <= endSimTime) {
        // 1. Ingest arriving packets
        while (socketIndex < socketStream.length && socketStream[socketIndex].arriveTime <= currentTime) {
            const p = socketStream[socketIndex];
            heap.push({
                packet: p,
                id: BigInt(p.msg.id),
                arriveTime: p.arriveTime
            });
            socketIndex++;
        }
        
        // 2. Sort Heap (Smallest ID first)
        heap.sort((a, b) => (a.id < b.id ? -1 : 1));
        
        // 3. Check Top
        if (heap.length > 0) {
            const top = heap[0];
            const age = currentTime - top.arriveTime;
            
            if (age >= CONFIG.BUFFER_WINDOW_MS) {
                // Render!
                finalLog.push(top.packet.msg);
                heap.shift(); // Remove top
                
                // Continue checking new top in same tick? 
                // Yes, multiple might be ready.
                continue; 
            }
        }
        
        currentTime += 10; // Tick 10ms
    }
    
    // Check Result
    let smartViolations = 0;
    lastId = 0n;
    finalLog.forEach(msg => {
        const currentId = BigInt(msg.id);
        if (currentId < lastId) {
            smartViolations++;
        }
        lastId = currentId;
    });
    
    console.log(`Smart Out-of-Order: ${smartViolations} / ${finalLog.length}`);
    console.log(`Smart Disorder Rate: ${(smartViolations / finalLog.length * 100).toFixed(2)}%`);
    
    if (smartViolations === 0) {
        console.log('\n✅ SUCCESS: Buffer Window successfully restored perfect order!');
    } else {
        console.log('\n❌ FAILURE: Buffer Window was too short for the network jitter.');
    }

});
