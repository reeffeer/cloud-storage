package server;

import commonClasses.AuthCommand;
import commonClasses.AuthResult;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.Files.*;
import static server.Server.STORAGE_DIR;


public class AuthHandler
        extends ChannelInboundHandlerAdapter {

    private boolean autorized;
    private final AuthService auth;


    public AuthHandler() {
        autorized = false;
        auth = new AuthService();
    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (autorized) {
            ctx.fireChannelRead(msg);
            return;
        }

        try {
            if (msg instanceof AuthCommand) {
                AuthCommand com = (AuthCommand) msg;

                autorized = auth.autorize(com.login, com.password);

                if (autorized) {
                    Path dir = Paths.get(STORAGE_DIR, com.login);
                    if (!exists(dir))
                        createDirectory(dir);

                    MainHandler handler = ctx.pipeline().get(MainHandler.class);
                    handler.setUserDir(dir);

                    ctx.writeAndFlush(AuthResult.ok());
                } else {
                    ctx.writeAndFlush(AuthResult.fail());
                }
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

}
