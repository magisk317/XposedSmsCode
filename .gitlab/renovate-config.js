module.exports = {
  platform: "gitlab",
  endpoint: process.env.CI_API_V4_URL || "https://gitlab.com/api/v4/",
  repositories: ["magisk3171/XposedSmsCode"],
  onboarding: false,
  requireConfig: "required",
  ignorePaths: [".github/**"],
  allowedUnsafeExecutions: ["gradleWrapper"],
  automerge: true,
  automergeType: "pr",
  automergeStrategy: "merge",
  platformAutomerge: true,
  gitAuthor: "Magisk317 <35032111-magisk731@users.noreply.gitlab.com>",
};
