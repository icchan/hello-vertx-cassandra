package net.bubblemix.hello

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.cassandra.CassandraClient
import io.vertx.cassandra.CassandraClientOptions
import io.vertx.core.Handler
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.Json
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext


class MainVerticle : AbstractVerticle() {

    override fun start(startFuture: Future<Void>) {

        var server = vertx.createHttpServer()

        var router = Router.router(vertx)

        router.route(HttpMethod.GET, "/nookmiles/:user").handler(getBalanceHandler)

        server.requestHandler(router).listen(8888) { http ->
            if (http.succeeded()) {
                startFuture.complete()
                println("Now listening on port 8888")
            } else {
                startFuture.fail(http.cause())
            }
        }
    }

    // Request handler for getting a user's balance
    private val getBalanceHandler = Handler<RoutingContext> { request ->

        val response = request.response()

        response.putHeader("content-type", "application/json")

        // input parameter
        val userId = request.pathParam("user")
        println("userId: $userId")

        // connect to cassandra
        val options = CassandraClientOptions().addContactPoint("localhost")
        val client = CassandraClient.createShared(vertx, "helloCassandraClient", options)

        // query cassandra
        client.prepare("SELECT userid, balance FROM helloks.miles where userid = ? ") { preparedStatementResult ->
            if (preparedStatementResult.succeeded()) {
                println("The query has successfully been prepared")
                val preparedStatement = preparedStatementResult.result()

                // execute the prepared statement
                client.execute(preparedStatement.bind(userId)) { done ->
                    val results = done.result()

                    // handle results here
                    results.one { one ->
                        if (one.succeeded()) {
                            val row = one.result()
                            println("One row successfully fetched: $row")

                            if (row != null) {
                                // send a response
                                response.end(Json.encodePrettily(Balance(name = row.getString(0), miles = row.getInt(1))))
                            } else {
                                // handle no rows returned
                                response.end(Json.encodePrettily(Error("user not found")))
                            }
                        } else {
                            println("Unable to fetch a row for user: $userId")
                            one.cause().printStackTrace()
                            response.statusCode = 500
                            response.end(Json.encodePrettily(Error("fetch error")))
                        }
                    }
                }

            } else {
                println("Unable to prepare the query")
                preparedStatementResult.cause().printStackTrace()

                // send a response
                response.statusCode = 500
                response.end(Json.encodePrettily(Error("query error")))
            }
        }

    }
}

data class Balance(var name: String, var miles: Int)

data class Error(var message: String)
