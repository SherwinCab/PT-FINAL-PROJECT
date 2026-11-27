import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mamang Sorbetero - animated FCFS / SSTF / SCAN.
 * The vendor (disk head) walks down a street (cylinders = house numbers) and stops at every house that calls.
 * Run: javac DiskScheduler.java MamangSorbeteroGUI.java && java MamangSorbeteroGUI
 */
public class MamangSorbeteroGUI extends JFrame {
    private static final String[] STRATEGIES = {
            "FCFS - first call, first served",
            "SSTF - nearest house first",
            "SCAN - walk to the end of the street, then back"};

    private final JTextField startField = new JTextField("53", 6);
    private final JTextField firstField = new JTextField("0", 6);
    private final JTextField lastField = new JTextField("199", 6);
    private final JComboBox<String> dirBox = new JComboBox<>(new String[]{
            "Toward the end of the street (increasing)",
            "Toward the start of the street (decreasing)"});
    private final JTextField callField = new JTextField("98 183 37 122 14 124 65 67", 36);

    private final JComboBox<String> stratBox = new JComboBox<>(STRATEGIES);
    private final JButton pauseBtn = new JButton("Pause");
    private final JButton replayBtn = new JButton("Replay");
    private final JSlider speed = new JSlider(1, 10, 5);
    private final JCheckBox soundBox = new JCheckBox("Sound", true);

    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel model =
            new DefaultTableModel(new Object[]{"Vendor strategy", "Total distance walked", "Route (order of stops)"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
    private final JTextArea report = new JTextArea();
    private final StreetPanel street = new StreetPanel();

    // results of the last computation
    private int[][] routes;
    private int curMin, curMax;
    private List<Integer> curCalls;

    public MamangSorbeteroGUI() {
        super("Mamang Sorbetero - FCFS / SSTF / SCAN");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        add(buildInputPanel(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.add(buildControlBar(), BorderLayout.NORTH);
        center.add(street, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(260);
        table.getColumnModel().getColumn(1).setMaxWidth(160);
        report.setEditable(false);
        report.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Comparison", new JScrollPane(table));
        tabs.addTab("Report", new JScrollPane(report));
        tabs.setPreferredSize(new Dimension(900, 170));
        add(tabs, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(1020, 780));
        pack();
        setLocationRelativeTo(null);
    }

    // ---------------- UI construction ----------------

    private JPanel buildInputPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Where is the vendor, and which houses are calling?"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridy = 0;
        c.gridx = 0; p.add(new JLabel("Vendor position (house #):"), c);
        c.gridx = 1; p.add(startField, c);
        c.gridx = 2; p.add(new JLabel("First house:"), c);
        c.gridx = 3; p.add(firstField, c);
        c.gridx = 4; p.add(new JLabel("Last house:"), c);
        c.gridx = 5; p.add(lastField, c);

        c.gridy = 1;
        c.gridx = 0; p.add(new JLabel("SCAN direction:"), c);
        c.gridx = 1; c.gridwidth = 3; p.add(dirBox, c);
        c.gridwidth = 1;

        c.gridy = 2;
        c.gridx = 0; p.add(new JLabel("Houses calling:"), c);
        c.gridx = 1; c.gridwidth = 5; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        p.add(callField, c);
        c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.weightx = 0;

        c.gridy = 3;
        c.gridx = 0; c.gridwidth = 6;
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton go = new JButton("Start selling");
        JButton sample = new JButton("Load sample");
        JButton clear = new JButton("Clear");
        go.addActionListener(e -> compute());
        sample.addActionListener(e -> loadSample());
        clear.addActionListener(e -> clearAll());
        buttons.add(go);
        buttons.add(sample);
        buttons.add(clear);
        status.setFont(status.getFont().deriveFont(Font.BOLD));
        buttons.add(status);
        p.add(buttons, c);

        startField.addActionListener(e -> compute());
        firstField.addActionListener(e -> compute());
        lastField.addActionListener(e -> compute());
        callField.addActionListener(e -> compute());
        getRootPane().setDefaultButton(go);
        return p;
    }

    private JPanel buildControlBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        bar.add(new JLabel("Watch strategy:"));
        bar.add(stratBox);
        bar.add(pauseBtn);
        bar.add(replayBtn);
        bar.add(new JLabel("Speed:"));
        speed.setPreferredSize(new Dimension(140, 24));
        bar.add(speed);
        bar.add(soundBox);
        soundBox.addActionListener(e -> Sfx.enabled = soundBox.isSelected());

        stratBox.addActionListener(e -> startAnimation());
        pauseBtn.addActionListener(e -> {
            street.playing = !street.playing;
            pauseBtn.setText(street.playing ? "Pause" : "Resume");
        });
        replayBtn.addActionListener(e -> startAnimation());
        speed.addChangeListener(e -> street.speedFactor = speed.getValue() / 5.0);
        return bar;
    }

    // ---------------- logic ----------------

    private static int parseInt(String label, String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a whole number (got \"" + text.trim() + "\").");
        }
    }

    private static long num(Object o) {
        return Long.parseLong(String.valueOf(o));
    }

    /** Turns the joined route text ("53 65 67 ...") back into numbers. */
    private static int[] toRoute(String joined) {
        String[] parts = joined.trim().split("\\s+");
        int[] r = new int[parts.length];
        for (int i = 0; i < parts.length; i++) r[i] = Integer.parseInt(parts[i]);
        return r;
    }

    private void compute() {
        try {
            DiskScheduler.Input in = new DiskScheduler.Input();
            in.head = parseInt("Vendor position", startField.getText());
            in.minCyl = parseInt("First house", firstField.getText());
            in.maxCyl = parseInt("Last house", lastField.getText());
            in.up = dirBox.getSelectedIndex() == 0;
            if (in.minCyl >= in.maxCyl) {
                showError("The first house must be smaller than the last house.");
                return;
            }
            if (callField.getText().trim().isEmpty()) {
                showError("No one is calling. Enter at least one house.");
                return;
            }
            try {
                in.req = DiskScheduler.parseList(callField.getText());
            } catch (IllegalArgumentException ex) {
                showError("Invalid list of houses. Use numbers only, separated by spaces or commas. (" + ex.getMessage() + ")");
                return;
            }

            String err = DiskScheduler.validate(in);
            if (err != null) {
                showError("Invalid input: " + err);
                return;
            }

            DiskScheduler.Result f = DiskScheduler.fcfs(in);
            DiskScheduler.Result s = DiskScheduler.sstf(in);
            DiskScheduler.Result sc = DiskScheduler.scan(in);

            String rf = DiskScheduler.join(DiskScheduler.path(in, f), " ");
            String rs = DiskScheduler.join(DiskScheduler.path(in, s), " ");
            String rc = DiskScheduler.join(DiskScheduler.path(in, sc), " ");

            model.setRowCount(0);
            model.addRow(new Object[]{STRATEGIES[0], f.total, rf});
            model.addRow(new Object[]{STRATEGIES[1], s.total, rs});
            model.addRow(new Object[]{STRATEGIES[2], sc.total, rc});
            report.setText(buildReport(in, f.total, s.total, sc.total, rf, rs, rc));
            report.setCaretPosition(0);

            // data for the animation
            routes = new int[][]{toRoute(rf), toRoute(rs), toRoute(rc)};
            curMin = in.minCyl;
            curMax = in.maxCyl;
            curCalls = new ArrayList<>();
            for (String t : callField.getText().trim().split("[\\s,;]+")) {
                if (!t.isEmpty()) curCalls.add(Integer.parseInt(t));
            }
            startAnimation();

            status.setForeground(new Color(0, 128, 0));
            status.setText("The vendor is on his way!");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (Exception ex) {
            showError("Unexpected error: " + ex);
        }
    }

    private void startAnimation() {
        if (routes == null) return;
        street.speedFactor = speed.getValue() / 5.0;
        Sfx.play(Sfx.START, 0);
        int i = stratBox.getSelectedIndex();
        street.load(curMin, curMax, routes[i], curCalls, STRATEGIES[i].split(" - ")[0]);
        pauseBtn.setText("Pause");
    }

    private String buildReport(DiskScheduler.Input in, Object tf, Object ts, Object tc,
                               String rf, String rs, String rc) {
        long a = num(tf), b = num(ts), d = num(tc);
        long best = Math.min(a, Math.min(b, d));
        StringBuilder winners = new StringBuilder();
        if (a == best) winners.append("FCFS ");
        if (b == best) winners.append("SSTF ");
        if (d == best) winners.append("SCAN ");

        StringBuilder sb = new StringBuilder();
        sb.append("=== MAMANG SORBETERO REPORT ===\n");
        sb.append("The vendor starts at house #").append(in.head)
          .append(" | Street: house #").append(in.minCyl).append(" to #").append(in.maxCyl).append('\n');
        sb.append("SCAN direction: ")
          .append(in.up ? "toward the end of the street" : "toward the start of the street").append('\n');
        sb.append("Houses calling: ").append(callField.getText().trim()).append("\n\n");

        sb.append("FCFS (first call, first served)\n");
        sb.append("  Total distance walked: ").append(a).append(" houses\n  Route: ").append(rf).append("\n\n");
        sb.append("SSTF (nearest house first)\n");
        sb.append("  Total distance walked: ").append(b).append(" houses\n  Route: ").append(rs).append("\n\n");
        sb.append("SCAN (walk to the end of the street, then turn back)\n");
        sb.append("  Total distance walked: ").append(d).append(" houses\n  Route: ").append(rc).append("\n\n");

        sb.append("SHORTEST WALK: ").append(winners.toString().trim())
          .append(" (").append(best).append(" houses)\n\n");
        sb.append("Notes:\n");
        sb.append("- FCFS is fair to the order of calls, but the vendor may walk a very long way.\n");
        sb.append("- SSTF usually gives a short walk, but a customer at the far end can be ignored for a long time ")
          .append("(starvation) if new calls keep coming from nearby houses.\n");
        sb.append("- SCAN walks all the way to the end of the street even if nobody is calling there, so it adds ")
          .append("extra walking, but no customer is left waiting indefinitely.\n");
        return sb.toString();
    }

    private void showError(String msg) {
        Sfx.play(Sfx.ERR, 0);
        status.setForeground(new Color(192, 0, 0));
        status.setText("Error: " + msg);
    }

    private void loadSample() {
        startField.setText("53");
        firstField.setText("0");
        lastField.setText("199");
        dirBox.setSelectedIndex(0);
        callField.setText("98 183 37 122 14 124 65 67");
        compute();
    }

    private void clearAll() {
        startField.setText("");
        firstField.setText("");
        lastField.setText("");
        dirBox.setSelectedIndex(0);
        callField.setText("");
        model.setRowCount(0);
        report.setText("");
        status.setText(" ");
        routes = null;
        street.clear();
        startField.requestFocusInWindow();
    }

    // ---------------- animated street ----------------

    private static class StreetPanel extends JPanel {
        volatile boolean playing = true;
        volatile double speedFactor = 1.0;

        private int min, max;
        private int[] route;
        private String stratName = "";
        private List<Integer> houses = new ArrayList<>();
        private int totalCalls;
        private Map<Integer, Integer> pending = new HashMap<>();
        private final Set<Integer> served = new HashSet<>();
        private int idx;
        private double pos, walked;
        private int sold, facing = 1;
        private boolean finished;
        private long pauseUntil, lastNanos = System.nanoTime();
        private int startMark;
        private long lastStepMs;

        StreetPanel() {
            setPreferredSize(new Dimension(900, 300));
            new Timer(16, e -> tick()).start();
        }

        void clear() {
            route = null;
            repaint();
        }

        void load(int min, int max, int[] route, List<Integer> calls, String name) {
            this.min = min;
            this.max = max;
            this.route = route;
            this.stratName = name;
            this.houses = new ArrayList<>(new TreeSet<>(calls));
            this.totalCalls = calls.size();
            this.pending = new HashMap<>();
            for (int c : calls) pending.merge(c, 1, Integer::sum);
            this.served.clear();
            this.sold = 0;
            this.walked = 0;
            this.idx = 1;
            this.finished = route.length <= 1;
            this.playing = true;
            this.pauseUntil = 0;
            this.pos = route[0];
            this.startMark = route[0];
            this.facing = route.length > 1 && route[1] < route[0] ? -1 : 1;
            Integer cnt = pending.remove(route[0]);
            if (cnt != null) {
                sold += cnt;
                served.add(route[0]);
                pauseUntil = System.currentTimeMillis() + 750;
                Sfx.play(Sfx.TING, 0);
            }
            lastNanos = System.nanoTime();
            repaint();
        }

        private void tick() {
            long nowN = System.nanoTime();
            double dt = Math.min(0.1, (nowN - lastNanos) / 1e9);
            lastNanos = nowN;
            if (route == null) return;
            long now = System.currentTimeMillis();
            if (playing && !finished && now >= pauseUntil) {
                double before = pos;
                double step = (max - min) * 0.18 * speedFactor * dt;
                int guard = 0;
                while (!finished && guard++ < 100) {
                    double target = route[idx];
                    double d = target - pos;
                    if (d != 0) facing = d > 0 ? 1 : -1;
                    if (Math.abs(d) <= step) {
                        walked += Math.abs(d);
                        step -= Math.abs(d);
                        pos = target;
                        arrive(now);
                        if (now < pauseUntil) break;
                    } else {
                        double mv = Math.signum(d) * step;
                        pos += mv;
                        walked += Math.abs(mv);
                        break;
                    }
                }
                long gap = (long) Math.max(110, 330 / speedFactor);
                if (Math.abs(pos - before) > 1e-9 && now - lastStepMs >= gap) {
                    lastStepMs = now;
                    Sfx.play(Sfx.STEP, 0);
                }
            }
            repaint();
        }

        private void arrive(long now) {
            int p = route[idx];
            Integer cnt = pending.remove(p);
            if (cnt != null) {
                sold += cnt;
                served.add(p);
                pauseUntil = now + 750;
                Sfx.play(Sfx.TING, 0);
            }
            idx++;
            if (idx >= route.length) {
                finished = true;
                Sfx.play(Sfx.DONE, cnt != null ? 900 : 0);
            }
        }

        private double xOf(double p, int w) {
            int margin = 60;
            double span = Math.max(1, max - min);
            return margin + (p - min) * (w - 2.0 * margin) / span;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int roadH = Math.max(46, (int) (h * 0.18));
            int roadY = (int) (h * 0.60);
            int walkY = roadY - 14;

            // sky, sun, clouds
            g.setPaint(new GradientPaint(0, 0, new Color(120, 195, 250), 0, roadY, new Color(255, 238, 205)));
            g.fillRect(0, 0, w, roadY);
            g.setColor(new Color(255, 214, 10));
            g.fillOval(w - 100, 16, 58, 58);
            g.setColor(new Color(255, 255, 255, 220));
            cloud(g, 90, 46);
            cloud(g, w / 2, 30);

            // sidewalk, road, grass
            g.setColor(new Color(205, 200, 190));
            g.fillRect(0, walkY, w, 14);
            g.setColor(new Color(72, 72, 80));
            g.fillRect(0, roadY, w, roadH);
            g.setColor(new Color(240, 240, 240));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{14f, 12f}, 0f));
            g.drawLine(0, roadY + roadH / 2, w, roadY + roadH / 2);
            g.setStroke(new BasicStroke(1f));
            g.setColor(new Color(112, 172, 92));
            g.fillRect(0, roadY + roadH, w, h - roadY - roadH);

            if (route == null) {
                g.setColor(new Color(60, 60, 60));
                g.setFont(getFont().deriveFont(Font.BOLD, 16f));
                center(g, "Enter the inputs above and press \"Start selling\"", w / 2, roadY - 70);
                g.dispose();
                return;
            }

            // street end labels and start flag
            g.setFont(getFont().deriveFont(Font.BOLD, 12f));
            g.setColor(Color.WHITE);
            g.setColor(new Color(255, 245, 157));
            center(g, "Street start (#" + min + ")", (int) xOf(min, w) + 20, roadY + roadH + 16);
            center(g, "Street end (#" + max + ")", (int) xOf(max, w) - 20, roadY + roadH + 16);
            flag(g, (int) xOf(startMark, w), roadY + roadH + 4);

            // houses
            drawHouses(g, w, walkY, roadY + roadH);

            // vendor
            drawVendor(g, (int) xOf(pos, w), roadY + roadH - 8);

            // heads-up display
            hud(g);
            if (finished) banner(g, w, 8);
            g.dispose();
        }

        private void cloud(Graphics2D g, int x, int y) {
            g.fillOval(x, y, 56, 26);
            g.fillOval(x + 20, y - 12, 44, 30);
            g.fillOval(x + 44, y, 50, 24);
        }

        private void center(Graphics2D g, String s, int cx, int y) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, cx - fm.stringWidth(s) / 2, y);
        }

        private void flag(Graphics2D g, int x, int y) {
            g.setColor(new Color(90, 60, 30));
            g.fillRect(x - 1, y - 2, 3, 22);
            g.setColor(new Color(230, 60, 60));
            Path2D f = new Path2D.Double();
            f.moveTo(x + 2, y - 2);
            f.lineTo(x + 18, y + 4);
            f.lineTo(x + 2, y + 10);
            f.closePath();
            g.fill(f);
            g.setColor(Color.WHITE);
            g.setFont(getFont().deriveFont(Font.PLAIN, 10f));
            g.drawString("start", x + 4, y + 26);
        }

        private void drawHouses(Graphics2D g, int w, int baseY, int roadEnd) {
            int n = Math.max(1, houses.size());
            int hw = (int) Math.max(24, Math.min(44, (w - 120) / (double) n * 0.8));
            Color[] walls = {new Color(255, 224, 178), new Color(197, 225, 165), new Color(179, 229, 252), new Color(248, 187, 208)};
            Font numFont = getFont().deriveFont(Font.BOLD, 11f);

            // back row (odd index) first, then front row, so neighbours never hide each other
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < houses.size(); i++) {
                    int row = i % 2;
                    if (row != (pass == 0 ? 1 : 0)) continue;
                    int pos = houses.get(i);
                    int cx = (int) xOf(pos, w);
                    boolean done = served.contains(pos);
                    int bw = row == 1 ? (int) (hw * 0.88) : hw;
                    int bh = (int) (bw * 0.85);
                    int x = cx - bw / 2;
                    int y = baseY - row * 16 - bh;

                    g.setColor(done ? new Color(200, 235, 200) : walls[i % walls.length]);
                    g.fillRect(x, y, bw, bh);
                    g.setColor(new Color(140, 70, 50));
                    Path2D roof = new Path2D.Double();
                    roof.moveTo(x - 3, y);
                    roof.lineTo(cx, y - bh * 0.55);
                    roof.lineTo(x + bw + 3, y);
                    roof.closePath();
                    g.fill(roof);
                    g.setColor(new Color(110, 80, 60));
                    g.fillRect(cx - bw / 8, y + bh / 2, Math.max(4, bw / 4), bh / 2);
                    g.setColor(new Color(150, 200, 240));
                    g.fillRect(x + 4, y + 6, bw / 4, bw / 4);

                    // speech bubble: full text only when there is room on its own row
                    double room = Double.MAX_VALUE;
                    if (i - 2 >= 0) room = Math.min(room, cx - xOf(houses.get(i - 2), w));
                    if (i + 2 < houses.size()) room = Math.min(room, xOf(houses.get(i + 2), w) - cx);
                    boolean wide = room >= 84;
                    int by = y - (int) (bh * 0.55) - 8 - row * 26;
                    g.setFont(getFont().deriveFont(Font.BOLD, 11f));
                    if (done) {
                        bubble(g, cx, by, wide ? "Yum!" : "", new Color(220, 255, 220));
                        cone(g, cx - (wide ? 20 : 0), by - 12, 9);
                    } else {
                        bubble(g, cx, by, wide ? "Sorbetes!" : "!", Color.WHITE);
                    }
                }
            }

            // house numbers under the road, alternating rows
            g.setFont(numFont);
            g.setColor(Color.WHITE);
            for (int i = 0; i < houses.size(); i++) {
                int pos = houses.get(i);
                center(g, "#" + pos, (int) xOf(pos, w), roadEnd + 32 + (i % 2) * 13);
            }
        }

        private void bubble(Graphics2D g, int cx, int bottomY, String text, Color fill) {
            FontMetrics fm = g.getFontMetrics();
            int tw = Math.max(18, fm.stringWidth(text)) + 14;
            int bh = 22;
            int x = cx - tw / 2, y = bottomY - bh;
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Double(x, y, tw, bh, 12, 12));
            Path2D tail = new Path2D.Double();
            tail.moveTo(cx - 4, y + bh - 1);
            tail.lineTo(cx + 4, y + bh - 1);
            tail.lineTo(cx, y + bh + 6);
            tail.closePath();
            g.fill(tail);
            g.setColor(new Color(90, 90, 90));
            g.draw(new RoundRectangle2D.Double(x, y, tw, bh, 12, 12));
            g.drawString(text, cx - fm.stringWidth(text) / 2, y + 15);
        }

        private void cone(Graphics2D g, int x, int y, int s) {
            g.setColor(new Color(222, 170, 90));
            Path2D c = new Path2D.Double();
            c.moveTo(x - s / 2.0, y);
            c.lineTo(x + s / 2.0, y);
            c.lineTo(x, y + s * 1.6);
            c.closePath();
            g.fill(c);
            g.setColor(new Color(250, 140, 180));
            g.fillOval(x - s / 2 - 1, y - s + 2, s + 2, s);
        }

        private void drawVendor(Graphics2D g, int x, int footY) {
            boolean selling = System.currentTimeMillis() < pauseUntil && !finished;
            Graphics2D v = (Graphics2D) g.create();
            v.translate(x, footY);
            v.scale(facing, 1);

            // cart
            v.setColor(new Color(250, 250, 250));
            v.fillRoundRect(2, -34, 46, 24, 6, 6);
            v.setColor(new Color(80, 150, 220));
            v.fillRect(2, -26, 46, 5);
            v.setColor(new Color(90, 90, 90));
            v.drawRoundRect(2, -34, 46, 24, 6, 6);
            v.setColor(new Color(60, 60, 60));
            v.fillOval(8, -14, 14, 14);
            v.fillOval(32, -14, 14, 14);
            v.setColor(new Color(200, 200, 200));
            v.fillOval(12, -10, 6, 6);
            v.fillOval(36, -10, 6, 6);

            // umbrella
            v.setColor(new Color(110, 80, 50));
            v.fillRect(25, -78, 3, 44);
            for (int i = 0; i < 6; i++) {
                v.setColor(i % 2 == 0 ? new Color(230, 60, 70) : new Color(250, 250, 250));
                v.fill(new Arc2D.Double(-8, -108, 68, 60, i * 30, 30, Arc2D.PIE));
            }

            // vendor (pushing from behind)
            v.setColor(new Color(240, 200, 160));
            v.fillOval(-22, -58, 14, 14);
            v.setColor(new Color(60, 120, 190));
            v.fillRoundRect(-24, -44, 18, 26, 8, 8);
            v.setColor(new Color(60, 60, 80));
            long t = System.currentTimeMillis() / 140;
            boolean moving = playing && !finished && !selling;
            int sw = moving ? (t % 2 == 0 ? 4 : -4) : 0;
            v.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            v.drawLine(-18, -18, -18 + sw, 0);
            v.drawLine(-12, -18, -12 - sw, 0);
            v.setColor(new Color(240, 200, 160));
            v.drawLine(-8, -38, 2, -30);

            // straw hat
            v.setColor(new Color(235, 200, 90));
            v.fillOval(-28, -60, 26, 7);
            v.fillArc(-23, -66, 16, 14, 0, 180);
            v.dispose();

            if (selling) {
                g.setFont(getFont().deriveFont(Font.BOLD, 14f));
                g.setColor(new Color(255, 140, 0));
                center(g, "Ting-ting! Here you go!", x + 10, footY - 120);
                cone(g, x + 60 * facing, footY - 52, 10);
            }
        }

        private void hud(Graphics2D g) {
            g.setColor(new Color(255, 255, 255, 215));
            g.fill(new RoundRectangle2D.Double(10, 8, 250, 74, 14, 14));
            g.setColor(new Color(60, 60, 60));
            g.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g.drawString("Strategy: " + stratName, 22, 30);
            g.setFont(getFont().deriveFont(Font.PLAIN, 13f));
            g.drawString("Distance walked: " + Math.round(walked) + " houses", 22, 50);
            g.drawString("Ice cream sold to: " + sold + " / " + totalCalls, 22, 69);
        }

        private void banner(Graphics2D g, int w, int y) {
            String s = "All customers served!  Total walk: " + Math.round(walked) + " houses";
            g.setFont(getFont().deriveFont(Font.BOLD, 18f));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(s) + 40;
            g.setColor(new Color(40, 140, 70, 235));
            g.fill(new RoundRectangle2D.Double(w / 2.0 - tw / 2.0, y, tw, 40, 20, 20));
            g.setColor(Color.WHITE);
            g.drawString(s, w / 2 - fm.stringWidth(s) / 2, y + 26);
        }
    }

    // ---------------- sound effects (synthesized, no audio files needed) ----------------

    private static final class Sfx {
        static final String TING = "ting", STEP = "step", DONE = "done", ERR = "err", START = "start";
        static volatile boolean enabled = true;
        private static volatile boolean noAudio;
        private static final float RATE = 22050f;
        private static final AudioFormat FMT = new AudioFormat(RATE, 16, 1, true, false);
        private static final Map<String, byte[]> BANK = new HashMap<>();
        private static final ScheduledExecutorService POOL = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sfx");
            t.setDaemon(true);
            return t;
        });

        static {
            // bell: two quick "ting"s
            double[] ting = new double[(int) (RATE * 0.7)];
            note(ting, 0.00, 1568.0, 0.50, 0.45, true);
            note(ting, 0.14, 1976.0, 0.50, 0.45, true);
            BANK.put(TING, pcm(ting));

            // soft footstep
            double[] step = new double[(int) (RATE * 0.07)];
            Random rnd = new Random(7);
            for (int i = 0; i < step.length; i++) {
                double t = i / RATE;
                step[i] = (Math.sin(2 * Math.PI * 95 * t) * 0.8 + (rnd.nextDouble() - 0.5) * 0.4)
                        * Math.exp(-t * 60) * 0.35;
            }
            BANK.put(STEP, pcm(step));

            // finish fanfare: C-E-G-C
            double[] done = new double[(int) (RATE * 1.0)];
            double[] fq = {523.25, 659.25, 783.99, 1046.5};
            for (int i = 0; i < fq.length; i++) {
                note(done, i * 0.13, fq[i], i == 3 ? 0.55 : 0.2, 0.35, false);
            }
            BANK.put(DONE, pcm(done));

            // start jingle (ice cream truck style)
            double[] start = new double[(int) (RATE * 0.55)];
            double[] sq = {659.25, 783.99, 659.25, 523.25};
            double[] at = {0.0, 0.11, 0.22, 0.33};
            double[] du = {0.1, 0.1, 0.1, 0.2};
            for (int i = 0; i < sq.length; i++) note(start, at[i], sq[i], du[i], 0.3, false);
            BANK.put(START, pcm(start));

            // error buzz: two low beeps
            double[] err = new double[(int) (RATE * 0.4)];
            for (int b = 0; b < 2; b++) {
                int from = (int) (RATE * b * 0.2);
                int len = (int) (RATE * 0.14);
                for (int i = 0; i < len && from + i < err.length; i++) {
                    double t = i / RATE;
                    double sq2 = Math.signum(Math.sin(2 * Math.PI * 160 * t));
                    err[from + i] += sq2 * 0.22 * Math.min(1, (len - i) / (RATE * 0.03));
                }
            }
            BANK.put(ERR, pcm(err));
        }

        /** Adds one note (optionally bell-like) into the buffer. */
        private static void note(double[] buf, double startSec, double freq, double dur, double vol, boolean bell) {
            int from = (int) (startSec * RATE);
            int n = (int) (dur * RATE);
            for (int i = 0; i < n && from + i < buf.length; i++) {
                double t = i / RATE;
                double env;
                double s;
                if (bell) {
                    env = Math.exp(-t * 8) * Math.min(1, t / 0.002);
                    s = Math.sin(2 * Math.PI * freq * t) + 0.35 * Math.sin(2 * Math.PI * freq * 2.76 * t);
                } else {
                    env = Math.min(1, t / 0.01) * Math.min(1, (dur - t) / 0.06);
                    s = Math.sin(2 * Math.PI * freq * t) + 0.25 * Math.sin(2 * Math.PI * freq * 2 * t);
                }
                buf[from + i] += s * env * vol;
            }
        }

        private static byte[] pcm(double[] d) {
            byte[] out = new byte[d.length * 2];
            for (int i = 0; i < d.length; i++) {
                int v = (int) Math.round(Math.max(-1, Math.min(1, d[i])) * 32000);
                out[2 * i] = (byte) (v & 0xFF);
                out[2 * i + 1] = (byte) ((v >> 8) & 0xFF);
            }
            return out;
        }

        static void play(String key, long delayMs) {
            if (!enabled || noAudio) return;
            byte[] data = BANK.get(key);
            if (data == null) return;
            POOL.schedule(() -> {
                try {
                    Clip clip = AudioSystem.getClip();
                    clip.open(FMT, data, 0, data.length);
                    clip.addLineListener(ev -> {
                        if (ev.getType() == LineEvent.Type.STOP) clip.close();
                    });
                    clip.start();
                } catch (IllegalArgumentException ex) {
                    noAudio = true;          // no audio device on this machine
                } catch (Exception ex) {
                    // line busy or unavailable: skip this one sound
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MamangSorbeteroGUI().setVisible(true));
    }
}