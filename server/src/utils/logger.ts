import pinoPkg from "pino";

const isDev = process.env.NODE_ENV !== "production";
const pino = (pinoPkg as any).default || pinoPkg;

export const logger = pino({
  level: isDev ? "debug" : "info",
  transport: isDev
    ? {
        target: "pino-pretty",
        options: {
          colorize: true,
          translateTime: "SYS:HH:MM:ss.l",
          ignore: "pid,hostname"
        }
      }
    : undefined,
});
