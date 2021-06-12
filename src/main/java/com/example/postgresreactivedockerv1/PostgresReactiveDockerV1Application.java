package com.example.postgresreactivedockerv1;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.rsocket.RSocketProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.xml.namespace.QName;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class PostgresReactiveDockerV1Application {

	public static void main(String[] args) {
		SpringApplication.run(PostgresReactiveDockerV1Application.class, args);
	}

}

@Configuration
class WebSocketConfig {
	@Bean
	SimpleUrlHandlerMapping simpleUrlHandlerMapping(WebSocketHandler wsh) {
		return new SimpleUrlHandlerMapping(){
			{
				setUrlMap(Map.of("/ws/greetings", wsh));
				setOrder(10);
			}
		};
	}

	@Bean
	WebSocketHandlerAdapter webSocketHandlerAdapter() {
		return new WebSocketHandlerAdapter();
	}

	@Bean
	WebSocketHandler webSocketHandler(GreetingProducer gp) {
		return new WebSocketHandler() {
			@Override
			public Mono<Void> handle(WebSocketSession session) {
				Flux<WebSocketMessage> response = session
						.receive()
						.map(webSocketMessage -> webSocketMessage.getPayloadAsText())
						.map(name -> new GreetingRequest(name))
						.flatMap(greetingRequest -> gp.greet(greetingRequest))
						.map(GreetingResponse::getMessage)
						.map(str -> session.textMessage(str));

				return session.send(response);
			}
		};
	}
}

@Component
class GreetingProducer {

	Flux<GreetingResponse> greet(GreetingRequest greetingRequest) {
		return Flux.fromStream(Stream.generate(() -> new GreetingResponse("Hello " + greetingRequest.getName() + " @ " + Instant.now())))
				.delayElements(Duration.ofSeconds(5));
	}
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingRequest {
	private String name;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingResponse {
	private String message;
}

@Configuration
class HttpConfiguration {
	@Bean
	RouterFunction<ServerResponse> routes(PersonRepository repository) {
		return route()
				.GET("/persons", serverRequest -> ServerResponse.ok().body(repository.findAll(), Person.class))
				.GET("/persons/{id}", serverRequest -> ServerResponse.ok().body(repository.findById(Integer.parseInt(serverRequest.pathVariable("id"))), Person.class))
				.build();
	}
}

@Configuration
@EnableR2dbcRepositories
class R2dbcConfiguration  extends AbstractR2dbcConfiguration {

	@Override
	public ConnectionFactory connectionFactory() {
		PostgresqlConnectionConfiguration config = PostgresqlConnectionConfiguration.builder()
				.host("localhost")
				.port(5432)
				.database("mydb3")
				.username("postgres")
				.password("admin")
				.build();
		return new PostgresqlConnectionFactory(config);
	}
}

@Component
@RequiredArgsConstructor
@Log4j2
class SimpleInitializr {

	private final PersonRepository repo;

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		var names = Flux
				.just("Carla", "Mariela", "Kitty", "Violeta", "Mike") //creates a flux of names
				.map(name -> new Person(null, name))        //makes ReservationR objects
				.flatMap(repo::save);								 //stores them into db

		repo
				.deleteAll()
				.thenMany(names)
				.thenMany(repo.findAll())
				.subscribe(log::info);
	}
}

@Repository
interface PersonRepository extends ReactiveCrudRepository<Person, Integer> {}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Person {
	@Id
	private Integer id;
	private String name;
}


