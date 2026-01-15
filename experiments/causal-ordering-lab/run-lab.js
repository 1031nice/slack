const { performance } = require('perf_hooks');

// ---------------------------------------------------------
// 1. Snowflake Generator (Simplified)
// ---------------------------------------------------------
class SnowflakeGenerator {
    constructor(machineId, clockOffset = 0) {
        this.machineId = machineId;
        this.clockOffset = clockOffset; // Simulate Clock Skew
        this.sequence = 0;
        this.lastTime = 0;
    }

    generate() {
        let time = Date.now() + this.clockOffset;
        
        if (time === this.lastTime) {
            this.sequence++;
        } else {
            this.sequence = 0;
            this.lastTime = time;
        }

        // Simple ID construction: Time(40b) + Machine(10b) + Seq(14b)
        // Note: JS numbers are doubles, so we simulate string comparison for 64-bit safety
        const id = `${time.toString().padStart(13, '0')}-${this.machineId.toString().padStart(3, '0')}-${this.sequence.toString().padStart(4, '0')}`;
        
        return {
            id: id,
            timestamp: time,
            content: `Message from Node ${this.machineId}`,
            realTime: Date.now() // Actual generation time for validation
        };
    }
}

// ---------------------------------------------------------
// 2. Simulation Components
// ---------------------------------------------------------

function simulateNetwork(messages, maxJitterMs) {
    return messages.map(msg => {
        const delay = Math.random() * maxJitterMs;
        return {
            ...msg,
            arrivalRealTime: msg.realTime + delay // When it actually reaches client
        };
    }).sort((a, b) => a.arrivalRealTime - b.arrivalRealTime); // Network shuffles them by arrival time
}

function calculateInversions(messages) {
    let inversions = 0;
    for (let i = 0; i < messages.length - 1; i++) {
        // Compare String IDs (lexicographical sort works for fixed-length padded strings)
        if (messages[i].id > messages[i+1].id) {
            inversions++;
        }
    }
    return inversions;
}

// ---------------------------------------------------------
// 3. Scenarios
// ---------------------------------------------------------

function runScenarioA_Jitter() {
    console.log('\n--- Scenario A: Network Jitter (0-200ms) ---');
    const nodes = [
        new SnowflakeGenerator(1, 0),
        new SnowflakeGenerator(2, 0),
        new SnowflakeGenerator(3, 0)
    ];

    // 1. Generate Messages
    const rawMessages = [];
    for(let i=0; i<300; i++) {
        // Round robin generation
        rawMessages.push(nodes[i % 3].generate()); 
    }

    // 2. Network Shuffle
    const receivedMessages = simulateNetwork(rawMessages, 200);

    // 3. Strategy: Naive Append
    const naiveInversions = calculateInversions(receivedMessages);
    console.log(`[Naive Append]      Inversions: ${naiveInversions} / 300 (Failed)`);

    // 4. Strategy: Ordered Insertion
    const sortedMessages = [...receivedMessages].sort((a, b) => a.id.localeCompare(b.id));
    const smartInversions = calculateInversions(sortedMessages);
    console.log(`[Ordered Insertion] Inversions: ${smartInversions} / 300 (Success)`);
}

function runScenarioB_ClockSkew() {
    console.log('\n--- Scenario B: Clock Skew (-5000ms drift) ---');
    const nodes = [
        new SnowflakeGenerator(1, 0),        // Normal
        new SnowflakeGenerator(2, 0),        // Normal
        new SnowflakeGenerator(3, -5000)     // Lagging by 5 seconds
    ];

    // 1. Generate Messages
    const rawMessages = [];
    // Node 1 sends "Question" at T=0
    const msg1 = nodes[0].generate(); 
    rawMessages.push(msg1);
    
    // Simulate 100ms processing...
    
    // Node 3 sends "Answer" at T=100 (Real Time), but T=-4900 (Local Time)
    const msg2 = nodes[2].generate();
    rawMessages.push(msg2);

    console.log(`User A (Normal) Sent: ${msg1.id} (Real: ${msg1.realTime})`);
    console.log(`User B (Lagging) Sent: ${msg2.id} (Real: ${msg2.realTime})`);

    // 2. Network Shuffle (Assume fast network)
    const receivedMessages = simulateNetwork(rawMessages, 10);

    // 3. Strategy: Ordered Insertion (Even with sorting!)
    const sortedMessages = [...receivedMessages].sort((a, b) => a.id.localeCompare(b.id));

    // Check if Answer comes AFTER Question
    const isCorrectOrder = sortedMessages.indexOf(msg2) > sortedMessages.indexOf(msg1);
    
    if (isCorrectOrder) {
        console.log(`[Result] Order Preserved. (Unexpected for Skew)`);
    } else {
        console.log(`[Result] Order VIOLATED.`);
        console.log(`User B's reply appeared BEFORE User A's question because B's clock is slow.`);
        console.log(`This confirms Snowflake IDs are vulnerable to Clock Skew.`);
    }
}

// ---------------------------------------------------------
// Main
// ---------------------------------------------------------
runScenarioA_Jitter();
runScenarioB_ClockSkew();
