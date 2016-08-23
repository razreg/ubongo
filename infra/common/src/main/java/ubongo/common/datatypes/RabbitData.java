package ubongo.common.datatypes;

import java.io.*;

public class RabbitData implements Serializable {
    private Task task;
    private String message;

    public RabbitData(Task task, String message) {
        this.task = task;
        this.message = message;
    }

    public Task getTask() {
        return task;
    }

    public String getMessage() {
        return message;
    }

    public byte[] getBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(this);
        oos.flush();
        oos.reset();
        byte[] bytes = baos.toByteArray();
        oos.close();
        baos.close();

        return bytes;
    }

    public static RabbitData fromBytes(byte[] body) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(body);
        ObjectInputStream ois = new ObjectInputStream(bis);
        RabbitData rabbitDataObj = (RabbitData) ois.readObject();
        ois.close();
        bis.close();
        return rabbitDataObj;
    }
}
