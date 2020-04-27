package net.bubblemix.hello

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.cassandra.CassandraClient
import io.vertx.cassandra.CassandraClientOptions
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router


class MainVerticle : AbstractVerticle() {

    override fun start(startFuture: Future<Void>) {

        var server = vertx.createHttpServer()

        var router = Router.router(vertx)

        router.route(HttpMethod.GET, "/nookmiles/:user").handler {
            routingContext ->

            val request = routingContext.request()

            // input parameter
            val userId = request.getParam("user") // TODO validate user

            routingContext.response()
                .putHeader("content-type", "text/plain")

            println("userId: $userId")

            // connect to cassandra
            val options = CassandraClientOptions().addContactPoint("localhost")
            val client = CassandraClient.createShared(vertx, "helloCassandraClient", options)

            // query cassandra
            client.prepare("SELECT * FROM helloks.miles where userid = ? ") { preparedStatementResult ->
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
                                // send a response
                                routingContext.response()
                                    .end("$row")

                            } else {
                                println("Unable to fetch a row")
                                one.cause().printStackTrace()

                                // send a response
                                routingContext.response().end("fetch error")
                            }
                        }

                    }

                } else {
                    println("Unable to prepare the query")
                    preparedStatementResult.cause().printStackTrace()

                    // send a response
                    routingContext.response().end("error")
                }
            }

        }

        server.requestHandler(router).listen(8888) { http ->
            if (http.succeeded()) {
                startFuture.complete()
                println("Now listening on port 8888")
            } else {
                startFuture.fail(http.cause())
            }
        }

    }
}
