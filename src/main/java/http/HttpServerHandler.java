package http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.cache.Cache;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import com.profile.MyProfile.Profile;

public class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	private static final ObjectMapper objectMapper = new ObjectMapper();
	private final Cache<Long, String> dataCache;

	public HttpServerHandler(Cache<Long, String> dataCache) {
		this.dataCache = dataCache;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
		System.out.println("HttpServerHandler.channelRead0()");
		if (msg.getMethod().equals(HttpMethod.POST) && msg.getUri().equals("/get/profile")) {
			System.out.println("Data ::: ");
			System.out.println(msg.content().toString(Charset.forName("UTF-8")));
			getProfile(ctx, msg);
		}
	}

	private void getProfile(ChannelHandlerContext ctx, FullHttpRequest data) {
		Properties producerProperties = setProperties();
		KafkaProducer<String, String> producer = new KafkaProducer<String, String>(producerProperties);
		FullHttpResponse response = respBuilder(data);
		ObjectNode reqData = parseData(data);

		Profile.Builder profile = Profile.newBuilder();
		profile.setFirstName(reqData.get("firstname").asText().toString());
		profile.setLastName(reqData.get("lastname").asText().toString());

		producer.send(new ProducerRecord<String, String>("my-profile", "my-profile", profile.toString()));
		producer.close();
		ctx.write(response.retain());
	}

	private ObjectNode parseData(FullHttpRequest req) {
		ObjectNode res;
		try {
			res = objectMapper.readValue(req.content().toString(Charset.forName("UTF-8")), ObjectNode.class);
			return res;
		} catch (IOException e) {
		}
		return null;
	}

	private FullHttpResponse respBuilder(FullHttpRequest data) {
		ByteBuf content = data.content();
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
		response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/json");
		response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, content.readableBytes());
		return response;
	}

	private static Properties setProperties() {
		Properties props = new Properties();
		try {
			InputStream resourceAsStream = HttpServerHandler.class.getResourceAsStream("/kafkaconfig.properties");
			props.load(resourceAsStream);
			return props;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return props;
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		ctx.flush();
	}
}
