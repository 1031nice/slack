const express = require('express');
const app = express();

// Increase JSON limit to avoid overhead from rejection, though payload is small
app.use(express.json({ limit: '1mb' }));

app.post('/push', (req, res) => {
  // Simulate minimal processing
  const msg = req.body;
  res.json({ success: true });
});

function startServer(port = 3000) {
  return app.listen(port, () => {
    // console.log(`REST Server running at http://localhost:${port}`);
  });
}

if (require.main === module) {
    startServer();
}

module.exports = startServer;
