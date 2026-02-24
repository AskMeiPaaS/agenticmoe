const express = require('express');
const path = require('path');
const app = express();
const PORT = 3000;

app.use(express.json());
app.use(express.static('public'));

let embedding_cache = [];

app.post('/api/cache', (req, res) => {
    const { query, response } = req.body;
    if (query && response) {
        embedding_cache.unshift({ query, response });
        if (embedding_cache.length > 10) {
            embedding_cache.pop();
        }
        res.status(200).json({ success: true });
    } else {
        res.status(400).json({ error: 'query and response are required' });
    }
});

app.get('/api/cache', (req, res) => {
    res.json(embedding_cache);
});

app.listen(PORT, () => {
    console.log(`Frontend running at http://localhost:${PORT}`);
    console.log(`Backend expected at http://localhost:8080`);
});