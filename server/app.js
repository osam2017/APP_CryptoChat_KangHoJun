// Setup basic express server
var express = require('express');
var app = express();
var server = require('http').createServer(app);
var io = require('socket.io')(server);
var mysql = require('mysql');
var port = process.env.PORT || 5004;

var connection = mysql.createConnection({
    host: 'localhost',
    query: {
        pool: true
    },
    user: 'root',
    password: 'root',
    database: 'osam'
});

app.get('/', function(req, res) {
    res.sendFile(__dirname + '/index.html');
});

server.listen(port, function() {
    console.log('Server listening at port %d', port);
});

// Routing
app.use(express.static(__dirname + '/public'));


// Login
app.get('/loginCheck/', function(req, res) {

    var sql = 'SELECT name FROM kanghojun WHERE ID = "' + req.query.militaryId + '" AND PASSWORD = "' + req.query.password + '"';
    connection.query(sql, function(err, rows, fields) {
        if (err) {
            res.sendStatus(400);
            console.log("LoginStatus : Err!");
            return;
        }
        if (rows.length == 0) {
            res.sendStatus(204);
            console.log("LoginStatus : login Failed");
        } else {
            console.log(rows);
            res.status(201).send(rows);
            console.log("LoginStatus : login OK!");
            res.end();
        }
    });

});


// Chatroom

var numUsers = 0;

io.on('connection', function(socket) {
    var addedUser = false;

    // when the client emits 'new message', this listens and executes
    socket.on('new message', function(data) {
        // we tell the client to execute 'new message'
        socket.broadcast.emit('new message', {
            username: socket.username,
            message: data
        });
        console.log('user: ' + socket.username + ' | message: ' + data);
    });

    // when the client emits 'add user', this listens and executes
    socket.on('add user', function(username) {
        if (addedUser) return;

        // we store the username in the socket session for this client
        socket.username = username;
        ++numUsers;
        addedUser = true;
        socket.emit('login', {
            numUsers: numUsers
        });
        // echo globally (all clients) that a person has connected
        socket.broadcast.emit('user joined', {
            username: socket.username,
            numUsers: numUsers
        });
        console.log('numUsers: ' + numUsers);
    });

    // when the client emits 'typing', we broadcast it to others
    socket.on('typing', function() {
        socket.broadcast.emit('typing', {
            username: socket.username
        });
    });

    // when the client emits 'stop typing', we broadcast it to others
    socket.on('stop typing', function() {
        socket.broadcast.emit('stop typing', {
            username: socket.username
        });
    });

    // when the user disconnects.. perform this
    socket.on('disconnect', function() {
        if (addedUser) {
            --numUsers;

            // echo globally that this client has left
            socket.broadcast.emit('user left', {
                username: socket.username,
                numUsers: numUsers
            });
        }
        console.log('numUsers: ' + numUsers);
    });
});