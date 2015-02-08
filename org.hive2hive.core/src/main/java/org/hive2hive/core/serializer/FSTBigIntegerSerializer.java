package org.hive2hive.core.serializer;

import java.io.IOException;
import java.math.BigInteger;

import org.nustaq.serialization.FSTBasicObjectSerializer;
import org.nustaq.serialization.FSTClazzInfo;
import org.nustaq.serialization.FSTClazzInfo.FSTFieldInfo;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;

@SuppressWarnings("rawtypes")
public class FSTBigIntegerSerializer extends FSTBasicObjectSerializer {

	@Override
	public void writeObject(FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo, FSTFieldInfo referencedBy,
			int streamPosition) throws IOException {
		byte[] value = ((BigInteger) toWrite).toByteArray();
		out.writeInt(value.length);
		out.write(value);
	}

	@Override
	public Object instantiate(Class objectClass, FSTObjectInput in, FSTClazzInfo serializationInfo, FSTFieldInfo referencee,
			int streamPosition) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		byte[] buf = new byte[in.readInt()];
		in.read(buf);
		BigInteger result = new BigInteger(buf);
		in.registerObject(result, streamPosition, serializationInfo, referencee);
		return result;
	}

}
