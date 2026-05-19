package com.cityledger.cityledger.service;

/**
 * HTML email templates for CityLedger transactional emails.
 * All templates use inline CSS for maximum email client compatibility.
 */
public class EmailTemplates {

    private static final String BASE_URL = "https://cityledger-qm0r.onrender.com";

    // ── Shared wrapper ─────────────────────────────────────────────────────────

    private static String wrap(String accentColor, String content) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width,initial-scale=1"/>
              <title>CityLedger</title>
            </head>
            <body style="margin:0;padding:0;background:#f0f4f8;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f0f4f8;padding:40px 16px;">
                <tr><td align="center">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="max-width:580px;">

                    <!-- Header -->
                    <tr>
                      <td style="background:#0f3460;border-radius:12px 12px 0 0;padding:28px 36px;text-align:center;">
                        <div style="font-size:22px;font-weight:900;color:#ffffff;letter-spacing:-0.5px;">
                          City<span style="color:#4ade80;">Ledger</span>
                        </div>
                        <div style="font-size:11px;color:rgba(255,255,255,0.55);margin-top:4px;letter-spacing:1px;text-transform:uppercase;">
                          Civic Complaint Platform
                        </div>
                      </td>
                    </tr>

                    <!-- Accent bar -->
                    <tr>
                      <td style="height:4px;background:%s;"></td>
                    </tr>

                    <!-- Body -->
                    <tr>
                      <td style="background:#ffffff;padding:36px;border-radius:0 0 12px 12px;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                        %s
                      </td>
                    </tr>

                    <!-- Footer -->
                    <tr>
                      <td style="padding:24px 36px;text-align:center;">
                        <p style="font-size:12px;color:#94a3b8;margin:0 0 8px;">
                          CityLedger — Blockchain-Verified Civic Complaint Platform
                        </p>
                        <p style="font-size:11px;color:#cbd5e1;margin:0;">
                          <a href="%s" style="color:#0f3460;text-decoration:none;">cityledger-qm0r.onrender.com</a>
                          &nbsp;·&nbsp; This is an automated message, please do not reply.
                        </p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(accentColor, content, BASE_URL);
    }

    private static String severityColor(String severity) {
        if (severity == null) return "#2563eb";
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "#dc2626";
            case "HIGH"     -> "#ea580c";
            case "MEDIUM"   -> "#ca8a04";
            default         -> "#2563eb";
        };
    }

    private static String detailRow(String label, String value) {
        return """
            <tr>
              <td style="padding:10px 0;border-bottom:1px solid #f1f5f9;font-size:13px;color:#64748b;width:40%%;">%s</td>
              <td style="padding:10px 0;border-bottom:1px solid #f1f5f9;font-size:13px;font-weight:700;color:#0f1f3d;text-align:right;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    // ── 1. Citizen — Report Submitted ──────────────────────────────────────────

    public static String reportSubmitted(
            String citizenName, long complaintId, String category,
            String severity, String location, String txHash) {

        String sevColor = severityColor(severity);
        String txLine = (txHash != null && !txHash.isBlank())
            ? "<a href=\"https://sepolia.etherscan.io/tx/" + txHash + "\" style=\"color:#2563eb;font-weight:700;word-break:break-all;\">View on Etherscan &rarr;</a>"
            : "<span style=\"color:#94a3b8;\">Processing...</span>";

        String body = """
            <!-- Greeting -->
            <p style="font-size:15px;color:#374151;margin:0 0 6px;">Hi <strong>%s</strong>,</p>
            <p style="font-size:14px;color:#64748b;margin:0 0 28px;line-height:1.6;">
              Your report has been successfully submitted and recorded on the blockchain.
            </p>

            <!-- Status badge -->
            <div style="text-align:center;margin-bottom:28px;">
              <span style="display:inline-block;background:#f0fdf4;border:2px solid #bbf7d0;color:#15803d;
                           font-size:13px;font-weight:700;padding:10px 24px;border-radius:20px;">
                &#10003; Report Filed &amp; Blockchain-Verified
              </span>
            </div>

            <!-- Details table -->
            <table width="100%%" cellpadding="0" cellspacing="0"
                   style="background:#f8fafc;border-radius:10px;padding:4px 16px;margin-bottom:24px;">
              %s
              %s
              %s
              %s
            </table>

            <!-- What happens next -->
            <div style="background:#eff6ff;border-left:4px solid #0f3460;border-radius:0 8px 8px 0;
                        padding:16px 20px;margin-bottom:24px;">
              <p style="font-size:13px;font-weight:700;color:#0f3460;margin:0 0 10px;">What happens next?</p>
              <p style="font-size:13px;color:#374151;margin:4px 0;">&#9658; Our AI has analyzed and categorized your report</p>
              <p style="font-size:13px;color:#374151;margin:4px 0;">&#9658; A municipal officer will review within 24 hours</p>
              <p style="font-size:13px;color:#374151;margin:4px 0;">&#9658; A field worker will be assigned to resolve it</p>
              <p style="font-size:13px;color:#374151;margin:4px 0;">&#9658; You&apos;ll receive an email when it&apos;s resolved</p>
            </div>

            <!-- Blockchain note -->
            <div style="background:#f8fafc;border-radius:8px;padding:14px 16px;margin-bottom:28px;font-size:12px;">
              <span style="color:#64748b;">&#9741; Blockchain TX: </span>%s
            </div>

            <!-- CTA button -->
            <div style="text-align:center;margin-bottom:8px;">
              <a href="%s/citizen/reports" style="display:inline-block;background:#0f3460;color:#ffffff;
                 text-decoration:none;font-size:14px;font-weight:700;padding:14px 32px;
                 border-radius:8px;">Track Your Report &rarr;</a>
            </div>

            <p style="font-size:13px;color:#94a3b8;text-align:center;margin:20px 0 0;">
              Thank you for helping make our city better!
            </p>
            """.formatted(
                citizenName,
                detailRow("Report ID", "#CL-" + complaintId),
                detailRow("Category", category != null ? category : "General"),
                detailRow("Severity", "<span style=\"color:" + sevColor + ";\">" + (severity != null ? severity : "N/A") + "</span>"),
                detailRow("Location", location != null ? location : "—"),
                txLine,
                BASE_URL
            );

        return wrap(sevColor, body);
    }

    // ── 2. Citizen — Issue Resolved ────────────────────────────────────────────

    public static String issueResolved(String citizenName, String issueTitle, long complaintId) {
        String body = """
            <p style="font-size:15px;color:#374151;margin:0 0 6px;">Hi <strong>%s</strong>,</p>
            <p style="font-size:14px;color:#64748b;margin:0 0 28px;line-height:1.6;">
              Great news! Your reported issue has been resolved by our field team.
            </p>

            <!-- Green check -->
            <div style="text-align:center;margin-bottom:28px;">
              <div style="display:inline-block;background:#f0fdf4;border:2px solid #86efac;
                          border-radius:50%%;width:64px;height:64px;line-height:64px;font-size:28px;">
                &#10003;
              </div>
              <p style="font-size:18px;font-weight:900;color:#15803d;margin:12px 0 4px;">Issue Resolved!</p>
              <p style="font-size:13px;color:#64748b;margin:0;">Your complaint has been addressed.</p>
            </div>

            <!-- Issue detail -->
            <div style="background:#f0fdf4;border:1px solid #bbf7d0;border-radius:10px;padding:16px 20px;margin-bottom:24px;">
              <p style="font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.8px;
                         color:#15803d;margin:0 0 6px;">Resolved Issue</p>
              <p style="font-size:14px;font-weight:700;color:#0f1f3d;margin:0 0 4px;">%s</p>
              <p style="font-size:12px;color:#64748b;margin:0;">#CL-%d</p>
            </div>

            <p style="font-size:13px;color:#475569;line-height:1.7;margin:0 0 24px;">
              Our field worker has completed the work at the reported location.
              If you feel the issue has not been fully addressed, please file a new report.
            </p>

            <div style="text-align:center;margin-bottom:8px;">
              <a href="%s/citizen/reports" style="display:inline-block;background:#15803d;color:#ffffff;
                 text-decoration:none;font-size:14px;font-weight:700;padding:14px 32px;border-radius:8px;">
                View Report History &rarr;
              </a>
            </div>

            <p style="font-size:13px;color:#94a3b8;text-align:center;margin:20px 0 0;">
              Thank you for being an active citizen of CityLedger!
            </p>
            """.formatted(citizenName, issueTitle, complaintId, BASE_URL);

        return wrap("#15803d", body);
    }

    // ── 3. Field Worker — Task Assigned ───────────────────────────────────────

    public static String taskAssigned(String workerName, String taskTitle, long complaintId,
                                       String category, String severity, String location) {
        String sevColor = severityColor(severity);
        String body = """
            <p style="font-size:15px;color:#374151;margin:0 0 6px;">Hi <strong>%s</strong>,</p>
            <p style="font-size:14px;color:#64748b;margin:0 0 28px;line-height:1.6;">
              A new task has been assigned to you. Please review the details below and address it promptly.
            </p>

            <!-- Severity badge -->
            <div style="text-align:center;margin-bottom:24px;">
              <span style="display:inline-block;background:%s;color:#ffffff;font-size:12px;
                           font-weight:800;padding:6px 18px;border-radius:20px;letter-spacing:0.5px;">
                %s PRIORITY
              </span>
            </div>

            <!-- Task details -->
            <div style="background:#f8fafc;border-radius:10px;padding:4px 16px;margin-bottom:24px;">
              <table width="100%%" cellpadding="0" cellspacing="0">
                %s
                %s
                %s
                %s
              </table>
            </div>

            <div style="text-align:center;margin-bottom:8px;">
              <a href="%s/field-worker/dashboard" style="display:inline-block;background:#0f3460;color:#ffffff;
                 text-decoration:none;font-size:14px;font-weight:700;padding:14px 32px;border-radius:8px;">
                Open Dashboard &rarr;
              </a>
            </div>

            <p style="font-size:13px;color:#94a3b8;text-align:center;margin:20px 0 0;">
              Please complete this task as soon as possible.
            </p>
            """.formatted(
                workerName,
                sevColor, severity != null ? severity : "NORMAL",
                detailRow("Task ID", "#CL-" + complaintId),
                detailRow("Issue", taskTitle),
                detailRow("Category", category != null ? category : "General"),
                detailRow("Location", location != null ? location : "—"),
                BASE_URL
            );

        return wrap(sevColor, body);
    }

    // ── 4. Officer — Critical Alert ────────────────────────────────────────────

    public static String criticalAlert(long complaintId, String title, String category,
                                        String severity, String location, String reportedAt,
                                        String description) {
        String body = """
            <!-- Alert banner -->
            <div style="background:#fef2f2;border:2px solid #fecaca;border-radius:10px;
                        padding:16px 20px;margin-bottom:24px;text-align:center;">
              <p style="font-size:13px;font-weight:800;color:#dc2626;margin:0;text-transform:uppercase;
                         letter-spacing:0.8px;">&#9888; Urgent Action Required</p>
              <p style="font-size:13px;color:#7f1d1d;margin:4px 0 0;">
                A %s issue has been reported and requires immediate attention.
              </p>
            </div>

            <!-- Details -->
            <div style="background:#f8fafc;border-radius:10px;padding:4px 16px;margin-bottom:24px;">
              <table width="100%%" cellpadding="0" cellspacing="0">
                %s
                %s
                %s
                %s
                %s
              </table>
            </div>

            <!-- Description -->
            <div style="background:#fff7ed;border-left:4px solid #ea580c;border-radius:0 8px 8px 0;
                        padding:14px 16px;margin-bottom:24px;">
              <p style="font-size:12px;font-weight:700;color:#9a3412;margin:0 0 6px;text-transform:uppercase;
                         letter-spacing:0.5px;">Description</p>
              <p style="font-size:13px;color:#374151;margin:0;line-height:1.6;">%s</p>
            </div>

            <div style="text-align:center;margin-bottom:8px;">
              <a href="%s/officer/queue" style="display:inline-block;background:#dc2626;color:#ffffff;
                 text-decoration:none;font-size:14px;font-weight:700;padding:14px 32px;border-radius:8px;">
                Review &amp; Assign Now &rarr;
              </a>
            </div>
            """.formatted(
                severity != null ? severity.toLowerCase() : "critical",
                detailRow("Report ID", "#CL-" + complaintId),
                detailRow("Title", title),
                detailRow("Category", category != null ? category : "—"),
                detailRow("Location", location != null ? location : "—"),
                detailRow("Reported At", reportedAt),
                description != null ? description : "No description provided.",
                BASE_URL
            );

        return wrap("#dc2626", body);
    }

    // ── 5. Officer — Daily Digest ──────────────────────────────────────────────

    public static String dailyDigest(int total, long critical, long high, long medium, long low,
                                      String issueRows) {
        String body = """
            <p style="font-size:15px;color:#374151;margin:0 0 4px;">Good morning,</p>
            <p style="font-size:14px;color:#64748b;margin:0 0 28px;line-height:1.6;">
              Here&apos;s your daily summary of pending civic issues that require attention.
            </p>

            <!-- Summary stats -->
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:24px;">
              <tr>
                <td style="padding:4px;">
                  <div style="background:#fef2f2;border-radius:10px;padding:16px;text-align:center;">
                    <div style="font-size:26px;font-weight:900;color:#dc2626;">%d</div>
                    <div style="font-size:11px;font-weight:700;color:#7f1d1d;text-transform:uppercase;letter-spacing:0.5px;">Total Pending</div>
                  </div>
                </td>
                <td style="padding:4px;">
                  <div style="background:#fef2f2;border-radius:10px;padding:16px;text-align:center;">
                    <div style="font-size:26px;font-weight:900;color:#dc2626;">%d</div>
                    <div style="font-size:11px;font-weight:700;color:#7f1d1d;text-transform:uppercase;letter-spacing:0.5px;">Critical</div>
                  </div>
                </td>
                <td style="padding:4px;">
                  <div style="background:#fff7ed;border-radius:10px;padding:16px;text-align:center;">
                    <div style="font-size:26px;font-weight:900;color:#ea580c;">%d</div>
                    <div style="font-size:11px;font-weight:700;color:#9a3412;text-transform:uppercase;letter-spacing:0.5px;">High</div>
                  </div>
                </td>
                <td style="padding:4px;">
                  <div style="background:#fefce8;border-radius:10px;padding:16px;text-align:center;">
                    <div style="font-size:26px;font-weight:900;color:#ca8a04;">%d</div>
                    <div style="font-size:11px;font-weight:700;color:#713f12;text-transform:uppercase;letter-spacing:0.5px;">Medium</div>
                  </div>
                </td>
              </tr>
            </table>

            <!-- Priority issues -->
            <p style="font-size:13px;font-weight:800;color:#0f1f3d;margin:0 0 12px;text-transform:uppercase;
                       letter-spacing:0.5px;">Top Priority Issues</p>
            <div style="background:#f8fafc;border-radius:10px;overflow:hidden;margin-bottom:24px;">
              <table width="100%%" cellpadding="0" cellspacing="0">
                <tr style="background:#0f3460;">
                  <th style="padding:10px 14px;font-size:11px;color:rgba(255,255,255,0.7);text-align:left;font-weight:700;text-transform:uppercase;letter-spacing:0.5px;">ID</th>
                  <th style="padding:10px 14px;font-size:11px;color:rgba(255,255,255,0.7);text-align:left;font-weight:700;text-transform:uppercase;letter-spacing:0.5px;">Issue</th>
                  <th style="padding:10px 14px;font-size:11px;color:rgba(255,255,255,0.7);text-align:left;font-weight:700;text-transform:uppercase;letter-spacing:0.5px;">Severity</th>
                  <th style="padding:10px 14px;font-size:11px;color:rgba(255,255,255,0.7);text-align:left;font-weight:700;text-transform:uppercase;letter-spacing:0.5px;">Location</th>
                </tr>
                %s
              </table>
            </div>

            <div style="text-align:center;margin-bottom:8px;">
              <a href="%s/officer/queue" style="display:inline-block;background:#0f3460;color:#ffffff;
                 text-decoration:none;font-size:14px;font-weight:700;padding:14px 32px;border-radius:8px;">
                Open Officer Queue &rarr;
              </a>
            </div>

            <p style="font-size:13px;color:#94a3b8;text-align:center;margin:20px 0 0;">
              Thank you for keeping our city running smoothly!
            </p>
            """.formatted(total, critical, high, medium, issueRows, BASE_URL);

        return wrap("#0f3460", body);
    }

    /** Build a single issue row for the daily digest table */
    public static String digestIssueRow(long id, String title, String severity, String location, boolean isEven) {
        String bg = isEven ? "#ffffff" : "#f8fafc";
        String sevColor = severityColor(severity);
        return """
            <tr style="background:%s;">
              <td style="padding:10px 14px;font-size:12px;font-weight:700;color:#0f3460;">#CL-%d</td>
              <td style="padding:10px 14px;font-size:12px;color:#374151;">%s</td>
              <td style="padding:10px 14px;">
                <span style="background:%s;color:#fff;font-size:10px;font-weight:800;padding:3px 8px;border-radius:10px;">%s</span>
              </td>
              <td style="padding:10px 14px;font-size:12px;color:#64748b;">%s</td>
            </tr>
            """.formatted(bg, id, title, sevColor, severity != null ? severity : "—", location != null ? location : "—");
    }
}
